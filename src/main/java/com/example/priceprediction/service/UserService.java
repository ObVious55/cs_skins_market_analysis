package com.example.priceprediction.service;

import com.example.priceprediction.entity.UserEntity;
import com.example.priceprediction.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${app.steam.api-key}")
    private String steamApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_KEY_PREFIX = "user:steam:";
    private static final String LOCK_KEY_PREFIX = "lock:user:steam:";
    private static final String NULL_CACHE_VALUE = "NULL_USER";

    // 正常缓存
    private static final long CACHE_BASE_HOURS = 24L;

    // 空值缓存
    private static final long NULL_CACHE_MINUTES = 5L;

    // 锁超时时间
    private static final long LOCK_EXPIRE_SECONDS = 10L;

    /**
     * 获取用户信息的核心方法
     */
    public SteamUserCache getAndCacheSteamUser(String steamId) {
        // 0. 参数校验
        if (!isValidSteamId(steamId)) {
            log.warn("非法 steamId: {}", steamId);
            return null;
        }

        String redisKey = REDIS_KEY_PREFIX + steamId;

        // 查Redis
        Object cachedObj = redisTemplate.opsForValue().get(redisKey);
        if (cachedObj != null) {
            if (NULL_CACHE_VALUE.equals(cachedObj)) {
                log.info("Redis 命中空值缓存, steamId={}", steamId);
                return null;
            }

            if (cachedObj instanceof SteamUserCache cached) {
                log.info("Redis 缓存命中，返回用户: {}", cached.getNickname());
                return cached;
            }
        }

        String lockKey = LOCK_KEY_PREFIX + steamId;
        String lockValue = UUID.randomUUID().toString();

        boolean locked = tryLock(lockKey, lockValue);

        if (!locked) {
            // 没拿到锁，说明可能已有线程在回源，稍等后重试一次缓存
            sleepMillis(80);

            Object retryObj = redisTemplate.opsForValue().get(redisKey);
            if (retryObj != null) {
                if (NULL_CACHE_VALUE.equals(retryObj)) {
                    return null;
                }
                if (retryObj instanceof SteamUserCache retryUser) {
                    return retryUser;
                }
            }

            // 再兜底查一次 MySQL，避免一直空转
            return userRepository.findBySteamId(steamId)
                    .map(entity -> {
                        SteamUserCache cacheObj = new SteamUserCache(
                                entity.getSteamId(),
                                entity.getNickname(),
                                entity.getAvatarUrl()
                        );
                        setUserCache(redisKey, cacheObj);
                        return cacheObj;
                    })
                    .orElse(null);
        }

        try {
            // 2. 拿到锁后，再查一次 Redis，防止别的线程刚刚已经回填
            Object doubleCheckObj = redisTemplate.opsForValue().get(redisKey);
            if (doubleCheckObj != null) {
                if (NULL_CACHE_VALUE.equals(doubleCheckObj)) {
                    return null;
                }
                if (doubleCheckObj instanceof SteamUserCache cached) {
                    return cached;
                }
            }

            // 查MySQL
            log.info("Redis 未命中，开始检索 MySQL 数据库: {}", steamId);
            Optional<UserEntity> optionalUser = userRepository.findBySteamId(steamId);
            if (optionalUser.isPresent()) {
                UserEntity entity = optionalUser.get();
                log.info("MySQL 命中用户信息: {}", entity.getNickname());

                SteamUserCache cacheObj = new SteamUserCache(
                        entity.getSteamId(),
                        entity.getNickname(),
                        entity.getAvatarUrl()
                );

                setUserCache(redisKey, cacheObj);
                return cacheObj;
            }

            // MySQL没有，请求Steam API
            log.info("数据库无记录，开始请求 Steam API: {}", steamId);
            SteamUserCache freshUser = fetchFromSteam(steamId);

            if (freshUser == null) {
                // 空值缓存，防止恶意请求反复穿透
                redisTemplate.opsForValue().set(redisKey, NULL_CACHE_VALUE, NULL_CACHE_MINUTES, TimeUnit.MINUTES);
                log.warn("Steam API 未返回有效用户，写入空值缓存: {}", steamId);
                return null;
            }

            // 存入MySQL（幂等）
            saveOrUpdateMysql(freshUser);

            // 写入Redis
            setUserCache(redisKey, freshUser);

            return freshUser;

        } finally {
            unlock(lockKey, lockValue);
        }
    }

    /**
     * 内部方法：发起真实的网络请求获取 Steam 资料
     */
    private SteamUserCache fetchFromSteam(String steamId) {
        String url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/" +
                "?key=" + steamApiKey + "&steamids=" + steamId;

        try {
            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (!StringUtils.hasText(jsonResponse)) {
                log.warn("Steam API 返回为空, steamId={}", steamId);
                return null;
            }

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode players = root.path("response").path("players");

            if (!players.isArray() || players.isEmpty()) {
                log.warn("Steam API 没有找到玩家资料, steamId={}", steamId);
                return null;
            }

            JsonNode playerNode = players.get(0);
            if (playerNode == null || playerNode.isMissingNode()) {
                return null;
            }

            String nickname = playerNode.path("personaname").asText("");
            String avatarUrl = playerNode.path("avatarfull").asText("");

            if (!StringUtils.hasText(nickname)) {
                log.warn("Steam API 返回的昵称为空, steamId={}", steamId);
                return null;
            }

            return new SteamUserCache(steamId, nickname, avatarUrl);

        } catch (Exception e) {
            log.error("请求 Steam API 发生错误, steamId={}, error={}", steamId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 内部方法：将获取到的资料持久化到 MySQL（幂等保存/更新）
     */
    private void saveOrUpdateMysql(SteamUserCache user) {
        try {
            Optional<UserEntity> optionalUser = userRepository.findBySteamId(user.getSteamId());

            UserEntity entity = optionalUser.orElseGet(UserEntity::new);
            entity.setSteamId(user.getSteamId());
            entity.setNickname(user.getNickname());
            entity.setAvatarUrl(user.getAvatarUrl());
            entity.setLastLoginAt(LocalDateTime.now());

            userRepository.save(entity);
            log.info("用户信息已持久化到 MySQL: {}", user.getNickname());

        } catch (DataIntegrityViolationException e) {
            // 如果 steam_id 有唯一索引，并发插入时可能冲突，这里兜底
            log.warn("并发写入 MySQL 冲突, steamId={}", user.getSteamId());
        } catch (Exception e) {
            log.error("保存用户信息到 MySQL 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 写入正常用户缓存，增加随机 TTL，降低雪崩风险
     */
    private void setUserCache(String redisKey, SteamUserCache cacheObj) {
        long randomMinutes = ThreadLocalRandom.current().nextLong(10, 61);
        long totalMinutes = CACHE_BASE_HOURS * 60 + randomMinutes;
        redisTemplate.opsForValue().set(redisKey, cacheObj, totalMinutes, TimeUnit.MINUTES);
    }

    /**
     * steamId 基础校验：17 位数字
     */
    private boolean isValidSteamId(String steamId) {
        return StringUtils.hasText(steamId) && steamId.matches("^\\d{17}$");
    }

    /**
     * 获取分布式锁
     */
    private boolean tryLock(String lockKey, String lockValue) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                LOCK_EXPIRE_SECONDS,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(success);
    }

    /**
     * 安全释放锁，避免误删别人的锁
     */
    private void unlock(String lockKey, String lockValue) {
        String lua = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """;

        try {
            redisTemplate.execute(
                    new DefaultRedisScript<>(lua, Long.class),
                    Collections.singletonList(lockKey),
                    lockValue
            );
        } catch (Exception e) {
            log.error("释放 Redis 锁失败, lockKey={}, error={}", lockKey, e.getMessage(), e);
        }
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
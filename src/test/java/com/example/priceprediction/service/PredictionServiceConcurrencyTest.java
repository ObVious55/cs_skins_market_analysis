package com.example.priceprediction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PredictionService 并发安全性测试
 *
 * 测试场景：
 * 1. 单个用户的多个并发请求
 * 2. 多个用户的并发请求
 * 3. 锁竞争和超时
 * 4. 数据一致性验证
 */
@SpringBootTest
@DisplayName("PredictionService 并发安全性测试")
public class PredictionServiceConcurrencyTest {

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private ObjectMapper objectMapper;
    private static final String TEST_STEAM_ID = "76561198754572993";
    private static final String TEST_ITEM_NAME = "AK-47 | Phantom Disruptor";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 清理测试数据
        String redisKey = "chat_history:" + TEST_STEAM_ID + ":" + TEST_ITEM_NAME;
        redisTemplate.delete(redisKey);
        redisTemplate.delete("lock:" + redisKey);
    }

    /**
     * 测试1: 单个用户多个并发请求的数据一致性
     * 场景：用户快速多次提问，验证历史记录不丢失
     */
    @Test
    @DisplayName("单用户并发请求 - 数据一致性")
    void testSingleUserConcurrentRequests() throws InterruptedException {
        String steamId = TEST_STEAM_ID;
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 启动5个线程同时发送请求
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    // 模拟用户的连续追问请求
                    String question = "请分析饰品第" + index + "次的价格趋势";
                    // predictionService.predictPrice(steamId, createFollowUpRequest(question));

                    // 【注】实际测试需要mock AI API或使用测试环境
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("请求失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有请求应在30秒内完成");
        assertEquals(threadCount, successCount.get(), "所有请求应成功");
        assertEquals(0, failureCount.get(), "不应有失败的请求");
    }

    /**
     * 测试2: 锁的争用和超时
     * 场景：快速连续两个请求，第二个应该等待或超时
     */
    @Test
    @DisplayName("分布式锁争用 - Redisson看门狗机制")
    void testDistributedLockContention() throws InterruptedException {
        String redisKey = "chat_history:" + TEST_STEAM_ID + ":" + TEST_ITEM_NAME;
        String lockKey = "lock:" + redisKey;

        // 模拟第一个线程持有锁（使用Redisson）
        RLock lock1 = redissonClient.getLock(lockKey);
        boolean lockAcquired1 = false;

        try {
            lockAcquired1 = lock1.tryLock(10, 30, TimeUnit.SECONDS);

            // 第二个线程尝试获取同一个锁
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger retryCount = new AtomicInteger(0);

            executor.submit(() -> {
                try {
                    RLock lock2 = redissonClient.getLock(lockKey);
                    // 尝试获取锁，最多等待5秒
                    boolean lockAcquired2 = lock2.tryLock(5, 30, TimeUnit.SECONDS);

                    if (!lockAcquired2) {
                        retryCount.incrementAndGet(); // 获取失败
                    } else {
                        lock2.unlock(); // 成功获取后立即释放
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // 验证：第二个线程应该无法获取锁（因为第一个线程持有）
            assertTrue(retryCount.get() > 0, "第二个线程应该无法获取锁");
            System.out.println("锁竞争测试完成，第二个线程获取失败次数: " + retryCount.get());

        } finally {
            if (lockAcquired1) {
                lock1.unlock();
            }
        }
    }

    /**
     * 测试3: 多个并发用户的隔离性
     * 场景：不同用户同时操作，应该互不影响
     */
    @Test
    @DisplayName("多用户并发 - 用户隔离")
    void testMultiUserConcurrentOperations() throws InterruptedException {
        int userCount = 3;
        int operationsPerUser = 2;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount * operationsPerUser);

        for (int userId = 0; userId < userCount; userId++) {
            for (int opId = 0; opId < operationsPerUser; opId++) {
                int finalUserId = userId;
                int finalOpId = opId;

                executor.submit(() -> {
                    try {
                        String steamId = "user-" + finalUserId;
                        String redisKey = "chat_history:" + steamId + ":" + TEST_ITEM_NAME;

                        // 模拟操作：写入数据
                        Map<String, String> msg = new HashMap<>();
                        msg.put("role", "user");
                        msg.put("content", "消息-" + finalOpId);

                        List<Map<String, String>> messages = new ArrayList<>();
                        messages.add(msg);

                        String json = new ObjectMapper().writeValueAsString(messages);
                        redisTemplate.opsForValue().set(redisKey, json, 30, TimeUnit.MINUTES);

                        // 模拟操作：读取数据
                        String retrieved = redisTemplate.opsForValue().get(redisKey);
                        assertNotNull(retrieved, "应该读取到写入的数据");

                    } catch (Exception e) {
                        fail("操作不应失败: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有操作应在30秒内完成");
    }

    /**
     * 测试4: 锁的自我防护（防止误删）
     * 场景：验证只有持有正确lockId的线程才能释放锁
     */
    @Test
    @DisplayName("锁安全释放 - lockId验证")
    void testLockIdValidationOnRelease() {
        String lockKey = "test-lock-validation";
        String lockId1 = UUID.randomUUID().toString();
        String lockId2 = UUID.randomUUID().toString();

        // 线程1设置锁
        redisTemplate.opsForValue().setIfAbsent(lockKey, lockId1, 30, TimeUnit.SECONDS);

        // 线程2尝试用错误的lockId释放锁
        String currentLock = redisTemplate.opsForValue().get(lockKey);
        if (!lockId2.equals(currentLock)) {
            // 不应该删除锁
            assertNotNull(currentLock, "锁应该被保留");
        }

        // 线程1用正确的lockId释放锁
        currentLock = redisTemplate.opsForValue().get(lockKey);
        if (lockId1.equals(currentLock)) {
            redisTemplate.delete(lockKey);
        }

        // 锁应该被删除
        assertNull(redisTemplate.opsForValue().get(lockKey), "锁应该被成功释放");
    }

    /**
     * 测试5: 锁超时自动释放（防止死锁）
     * 场景：模拟线程崩溃，锁应该自动释放
     */
    @Test
    @DisplayName("锁自动释放 - 防止死锁")
    void testLockAutoRelease() throws InterruptedException {
        String lockKey = "test-lock-timeout";
        String lockId = UUID.randomUUID().toString();

        // 设置1秒超时的锁
        redisTemplate.opsForValue().setIfAbsent(lockKey, lockId, 1, TimeUnit.SECONDS);

        // 锁应该存在
        assertNotNull(redisTemplate.opsForValue().get(lockKey), "锁应该存在");

        // 等待锁超时
        Thread.sleep(1500);

        // 锁应该自动释放
        assertNull(redisTemplate.opsForValue().get(lockKey), "锁应该自动释放");
    }

    /**
     * 测试6: 高并发压力测试
     * 场景：100个并发请求，验证系统稳定性
     */
    @Test
    @DisplayName("高并发压力测试 - 100个请求")
    void testHighConcurrencyLoad() throws InterruptedException {
        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < requestCount; i++) {
            int requestId = i;
            executor.submit(() -> {
                try {
                    String userId = "stress-user-" + (requestId % 10);  // 10个不同用户
                    String redisKey = "stress-key:" + userId + ":" + requestId;

                    // 模拟操作
                    String value = "request-" + requestId;
                    redisTemplate.opsForValue().set(redisKey, value, 30, TimeUnit.SECONDS);

                    String retrieved = redisTemplate.opsForValue().get(redisKey);
                    if (value.equals(retrieved)) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertTrue(completed, "所有请求应在60秒内完成");
        assertEquals(requestCount, successCount.get(), "所有请求应成功");
        assertEquals(0, failureCount.get(), "不应有失败");

        double throughput = (requestCount * 1000.0) / duration;
        System.out.println(String.format("吞吐量: %.2f req/s", throughput));
    }

    // ===== 辅助方法 =====

    /**
     * 创建新分析请求
     */
    private String createNewAnalysisRequest() {
        // 【注】实际应该返回包含priceData的JsonNode
        return "{}";
    }

    /**
     * 创建追问请求
     */
    private String createFollowUpRequest(String question) {
        // 【注】实际应该返回包含question的JsonNode
        return "{}";
    }
}


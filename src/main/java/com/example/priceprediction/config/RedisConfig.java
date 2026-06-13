package com.example.priceprediction.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 1. 直接使用 RedisSerializer 提供的静态工厂方法
        // 这是目前 Spring 官方最推荐、最简洁的写法，彻底解决过时警告
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // 2. 设置 Key 为 String 序列化
        template.setKeySerializer(RedisSerializer.string());
        // 3. 设置 Value 为 JSON 序列化
        template.setValueSerializer(jsonSerializer);

        // Hash 类型的设置
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redisson 客户端配置
     * 用于分布式锁的看门狗机制
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 单机模式配置
        String address = "redis://" + redisHost + ":" + redisPort;
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            config.useSingleServer()
                    .setAddress(address)
                    .setPassword(redisPassword);
        } else {
            config.useSingleServer()
                    .setAddress(address);
        }

        // 连接池配置
        config.useSingleServer()
                .setConnectionMinimumIdleSize(10)
                .setConnectionPoolSize(64)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        // 锁配置
        config.setLockWatchdogTimeout(30000); // 看门狗超时时间：30秒

        return Redisson.create(config);
    }
}
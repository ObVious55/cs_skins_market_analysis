package com.example.priceprediction.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SteamInventoryRateLimiter {

    private static final String LIMITER_KEY = "steam:inventory:rate-limiter";

    private final RedissonClient redissonClient;
    private RRateLimiter rateLimiter;

    @Value("${app.steam.inventory-rate-limit.permits:30}")
    private long permits;

    @Value("${app.steam.inventory-rate-limit.interval:1}")
    private long interval;

    @Value("${app.steam.inventory-rate-limit.unit:MINUTES}")
    private RateIntervalUnit intervalUnit;

    public SteamInventoryRateLimiter(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        rateLimiter = redissonClient.getRateLimiter(LIMITER_KEY);
        rateLimiter.setRate(RateType.OVERALL, permits, interval, intervalUnit);
        log.info("Steam inventory rate limiter configured: {} permits / {} {}", permits, interval, intervalUnit);
    }

    public void acquire() {
        rateLimiter.acquire();
    }
}

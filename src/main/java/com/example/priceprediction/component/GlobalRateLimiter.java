package com.example.priceprediction.component;

import org.springframework.stereotype.Component;

@Component
public class GlobalRateLimiter {
    private long lastRequestTime = 0;
    private final long RATE_LIMIT = 1200; // 1.2秒

    /**
     * 对应 Node.js 中的 rateLimiter(req, res, next)
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequestTime;

        if (timeSinceLastRequest < RATE_LIMIT) {
            long waitTime = RATE_LIMIT - timeSinceLastRequest;
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
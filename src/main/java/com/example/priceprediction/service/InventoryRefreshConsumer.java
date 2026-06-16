package com.example.priceprediction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class InventoryRefreshConsumer {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private InventoryRefreshJobService jobService;

    @KafkaListener(topics = "inventory-refresh-topic", groupId = "inventory-refresh-group")
    public void consumeMessage(Map<String, Object> message, Acknowledgment acknowledgment) {
        String requestId = (String) message.get("requestId");
        String steamId = (String) message.get("steamId");
        if (steamId == null) {
            log.warn("Inventory refresh message missing steamId: {}", message);
            acknowledgment.acknowledge();
            return;
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = buildLegacyRequestId(message, steamId);
            log.warn("Inventory refresh message missing requestId, fallback requestId={}, message={}", requestId, message);
        }

        InventoryRefreshJobService.BeginResult beginResult = jobService.begin(requestId, steamId);
        if (!beginResult.isShouldProcess()) {
            if (beginResult.getSkipReason() == InventoryRefreshJobService.JobSkipReason.ALREADY_DONE) {
                log.info("Inventory refresh job already done, ack message. requestId={}, steamId={}", requestId, steamId);
                acknowledgment.acknowledge();
                return;
            }
            throw new IllegalStateException("Inventory refresh job is processing, retry later. requestId="
                    + requestId + ", steamId=" + steamId);
        }

        String lockKey = "inventory_refresh_consume_lock:" + steamId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120));
        if (Boolean.FALSE.equals(acquired)) {
            log.info("Inventory refresh lock is held, retry later. requestId={}, steamId={}", requestId, steamId);
            IllegalStateException exception = new IllegalStateException("Inventory refresh lock is held, retry later. requestId="
                    + requestId + ", steamId=" + steamId);
            jobService.markFailed(requestId, exception);
            throw exception;
        }

        try {
            log.info("Start inventory refresh job. requestId={}, steamId={}, reason={}",
                    requestId, steamId, beginResult.getStartReason());
            inventoryService.doRefreshInventory(steamId);
            jobService.markDone(requestId);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            jobService.markFailed(requestId, e);
            throw e;
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.warn("Failed to release inventory refresh lock, key={}, err={}", lockKey, e.getMessage());
            }
        }
    }

    private String buildLegacyRequestId(Map<String, Object> message, String steamId) {
        Object timestamp = message.get("timestamp");
        if (timestamp != null) {
            return steamId + ":" + timestamp;
        }
        return steamId + ":" + UUID.randomUUID();
    }
}

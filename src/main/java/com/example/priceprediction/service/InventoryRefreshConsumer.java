package com.example.priceprediction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
@Slf4j
@Service
public class InventoryRefreshConsumer {
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @KafkaListener(topics = "inventory-refresh-topic", groupId = "inventory-refresh-group")
    public void consumeMessage(Map<String, Object> message) {
        String steamId = (String) message.get("steamId");
        if (steamId == null) {
            log.warn("收到的库存刷新消息缺少 steamId: {}", message);
            return;
        }

        String lockKey = "inventory_refresh_consume_lock:" + steamId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(120));
        if (Boolean.FALSE.equals(acquired)) {
            log.info("已有消费者在处理此用户的库存刷新，跳过本次消息, SteamID: {}", steamId);
            return;
        }

        try {
            log.info("消费到库存刷新消息, SteamID: {}", steamId);
            inventoryService.doRefreshInventory(steamId);
        } finally {
            try {
                redisTemplate.delete(lockKey);
            } catch (Exception e) {
                log.warn("释放库存刷新消费者锁失败, key={}, err={}", lockKey, e.getMessage());
            }
        }
    }
}
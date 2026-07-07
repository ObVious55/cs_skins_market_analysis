package com.example.priceprediction.service;

import com.example.priceprediction.react.ToolObservation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class ToolObservationMemoryService {

    private static final String PREFIX = "agent_memory:tool_observation:";
    private static final Duration SUCCESS_TTL = Duration.ofMinutes(10);
    private static final Duration FAILURE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolObservationMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void remember(String memoryId, String toolName, ToolObservation observation, long latencyMs) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(toolName) || observation == null) {
            return;
        }

        ToolObservationSnapshot snapshot = new ToolObservationSnapshot();
        snapshot.setMemoryId(memoryId);
        snapshot.setToolName(toolName);
        snapshot.setItemId(extractItemId(observation.getData()));
        snapshot.setStatus(observation.getStatus());
        snapshot.setSuccess(observation.isSuccess());
        snapshot.setData(observation.getData());
        snapshot.setErrorMessage(observation.getErrorMessage());
        snapshot.setFallbackMessage(observation.getFallbackMessage());
        snapshot.setStep(observation.getStep());
        snapshot.setLatencyMs(latencyMs);
        snapshot.setTimestampMillis(observation.getTimestampMillis());

        try {
            String json = objectMapper.writeValueAsString(snapshot);
            Duration ttl = observation.isSuccess() ? SUCCESS_TTL : FAILURE_TTL;
            redisTemplate.opsForValue().set(lastKey(memoryId, toolName), json, ttl);
            if (StringUtils.hasText(snapshot.getItemId())) {
                redisTemplate.opsForValue().set(itemKey(memoryId, toolName, snapshot.getItemId()), json, ttl);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to persist tool observation memory, tool={}, memoryId={}", toolName, memoryId, e);
        }
    }

    private String extractItemId(Map<String, Object> data) {
        if (data == null || data.get("itemId") == null) {
            return "";
        }
        return data.get("itemId").toString();
    }

    private String lastKey(String memoryId, String toolName) {
        return PREFIX + memoryId + ":last:" + toolName;
    }

    private String itemKey(String memoryId, String toolName, String itemId) {
        return PREFIX + memoryId + ":item:" + itemId + ":" + toolName;
    }
}

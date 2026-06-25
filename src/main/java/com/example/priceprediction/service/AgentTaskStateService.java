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
import java.util.Optional;

@Slf4j
@Service
public class AgentTaskStateService {

    private static final String PREFIX = "agent_memory:task_state:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentTaskStateService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateFromToolObservation(String memoryId, String toolName, ToolObservation observation) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(toolName) || observation == null) {
            return;
        }

        AgentTaskState state = read(memoryId);
        state.setMemoryId(memoryId);
        state.setCurrentTask(inferTask(toolName));
        state.setLastToolName(toolName);
        state.setLastToolStatus(observation.getStatus());
        state.setUpdatedAtMillis(System.currentTimeMillis());

        Map<String, Object> data = observation.getData();
        if (data != null) {
            Object itemId = data.get("itemId");
            if (itemId != null && !StringUtils.hasText(state.getCurrentItemId())) {
                state.setCurrentItemId(itemId.toString());
            }
            Object marketHashName = data.get("marketHashName");
            if (marketHashName != null) {
                state.setCurrentMarketHashName(marketHashName.toString());
            }
        }
        state.setNextAction(observation.isSuccess() ? "summarize_or_continue" : "explain_unavailable_or_retry");
        write(state);
    }

    public Optional<AgentTaskState> find(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return Optional.empty();
        }
        AgentTaskState state = read(memoryId);
        if (!StringUtils.hasText(state.getCurrentItemId()) || !state.isItemConfirmed()) {
            return Optional.empty();
        }
        return Optional.of(state);
    }

    public void confirmItem(
            String memoryId,
            String itemId,
            String primaryName,
            String marketHashName,
            String source
    ) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(itemId)) {
            return;
        }

        AgentTaskState state = read(memoryId);
        state.setMemoryId(memoryId);
        state.setCurrentItemId(itemId.trim());
        state.setCurrentPrimaryName(primaryName);
        state.setCurrentMarketHashName(marketHashName);
        state.setItemConfirmed(true);
        state.setConfirmationSource(source);
        state.setConfirmedAtMillis(System.currentTimeMillis());
        state.setUpdatedAtMillis(System.currentTimeMillis());
        state.setNextAction("use_confirmed_item_for_follow_up");
        write(state);
    }

    public Optional<String> validateConfirmedItem(String memoryId, String requestedItemId) {
        if (!StringUtils.hasText(memoryId) || !StringUtils.hasText(requestedItemId)) {
            return Optional.of("Missing memoryId or itemId for confirmed item validation.");
        }

        AgentTaskState state = read(memoryId);
        if (!state.isItemConfirmed() || !StringUtils.hasText(state.getCurrentItemId())) {
            return Optional.of("No confirmed item in task state; ask user to confirm the item before tool calling.");
        }

        if (!state.getCurrentItemId().equals(requestedItemId.trim())) {
            return Optional.of("Tool itemId " + requestedItemId
                    + " does not match confirmed task itemId " + state.getCurrentItemId()
                    + "; do not switch item by tool call.");
        }

        return Optional.empty();
    }

    private AgentTaskState read(String memoryId) {
        String json = redisTemplate.opsForValue().get(key(memoryId));
        if (!StringUtils.hasText(json)) {
            return new AgentTaskState();
        }
        try {
            return objectMapper.readValue(json, AgentTaskState.class);
        } catch (Exception e) {
            log.warn("Failed to read agent task state, memoryId={}", memoryId, e);
            return new AgentTaskState();
        }
    }

    private void write(AgentTaskState state) {
        try {
            redisTemplate.opsForValue().set(key(state.getMemoryId()), objectMapper.writeValueAsString(state), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to write agent task state, memoryId={}", state.getMemoryId(), e);
        }
    }

    private String inferTask(String toolName) {
        return switch (toolName) {
            case "getItemPriceData" -> "PRICE_ANALYSIS";
            case "runItemStrategyAnalysis" -> "STRATEGY_ANALYSIS";
            case "getUserInventory" -> "INVENTORY_LOOKUP";
            default -> "UNKNOWN";
        };
    }

    private String key(String memoryId) {
        return PREFIX + memoryId;
    }
}

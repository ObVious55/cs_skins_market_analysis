package com.example.priceprediction.react;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReActExecutionGuard {

    private static final int MAX_STEPS_PER_TURN = 6;
    private final Map<String, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    public void beginTurn(String memoryId) {
        stepCounters.put(key(memoryId), new AtomicInteger(0));
    }

    public StepPermit nextStep(String memoryId) {
        int step = stepCounters.computeIfAbsent(key(memoryId), ignored -> new AtomicInteger(0))
                .incrementAndGet();
        return new StepPermit(step, step <= MAX_STEPS_PER_TURN);
    }

    private String key(String memoryId) {
        return StringUtils.hasText(memoryId) ? memoryId : "anonymous";
    }

    public record StepPermit(int step, boolean allowed) {
    }
}

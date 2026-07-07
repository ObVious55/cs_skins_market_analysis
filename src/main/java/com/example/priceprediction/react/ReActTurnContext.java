package com.example.priceprediction.react;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

@Component
public class ReActTurnContext {

    private final ThreadLocal<String> currentMemoryId = new ThreadLocal<>();
    private final ThreadLocal<String> currentTraceId = new ThreadLocal<>();
    private final ThreadLocal<Set<String>> currentAllowedTools = new ThreadLocal<>();

    public void bind(String memoryId) {
        if (StringUtils.hasText(memoryId)) {
            currentMemoryId.set(memoryId);
        }
        if (!StringUtils.hasText(currentTraceId.get())) {
            currentTraceId.set(UUID.randomUUID().toString());
        }
    }

    public void bind(String memoryId, String traceId) {
        bind(memoryId);
        if (StringUtils.hasText(traceId)) {
            currentTraceId.set(traceId);
        }
    }

    public void bind(String memoryId, Set<String> allowedTools) {
        bind(memoryId);
        currentAllowedTools.set(allowedTools == null ? Set.of() : Set.copyOf(allowedTools));
    }

    public String currentMemoryId() {
        return currentMemoryId.get();
    }

    public String currentTraceId() {
        return currentTraceId.get();
    }

    public boolean isToolAllowed(String toolName) {
        Set<String> allowedTools = currentAllowedTools.get();
        return allowedTools != null && allowedTools.contains(toolName);
    }

    public Set<String> currentAllowedTools() {
        Set<String> allowedTools = currentAllowedTools.get();
        return allowedTools == null ? Set.of() : allowedTools;
    }

    public void clear() {
        currentMemoryId.remove();
        currentTraceId.remove();
        currentAllowedTools.remove();
    }
}

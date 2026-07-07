package com.example.priceprediction.react;

import java.util.Collections;
import java.util.Map;

public class ToolObservation {

    private final String toolName;
    private final String status;
    private final boolean success;
    private final Map<String, Object> data;
    private final String errorMessage;
    private final String fallbackMessage;
    private final int step;
    private final long timestampMillis;

    private ToolObservation(
            String toolName,
            String status,
            boolean success,
            Map<String, Object> data,
            String errorMessage,
            String fallbackMessage,
            int step
    ) {
        this.toolName = toolName;
        this.status = status;
        this.success = success;
        this.data = data == null ? Collections.emptyMap() : data;
        this.errorMessage = errorMessage;
        this.fallbackMessage = fallbackMessage;
        this.step = step;
        this.timestampMillis = System.currentTimeMillis();
    }

    public static ToolObservation success(String toolName, Map<String, Object> data, int step) {
        return new ToolObservation(toolName, "SUCCESS", true, data, null, null, step);
    }

    public static ToolObservation validationError(String toolName, String errorMessage, int step) {
        return new ToolObservation(toolName, "VALIDATION_ERROR", false, null, errorMessage, null, step);
    }

    public static ToolObservation blocked(String toolName, String errorMessage, int step) {
        return new ToolObservation(toolName, "BLOCKED", false, null, errorMessage, null, step);
    }

    public static ToolObservation degraded(String toolName, String errorMessage, String fallbackMessage, int step) {
        return new ToolObservation(toolName, "DEGRADED", false, null, errorMessage, fallbackMessage, step);
    }

    public String getToolName() {
        return toolName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public int getStep() {
        return step;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}

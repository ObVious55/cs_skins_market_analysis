package com.example.priceprediction.react;

import java.util.Map;

public class ToolTraceStep {

    private final int step;
    private final String toolName;
    private final Map<String, String> arguments;
    private final String status;
    private final long latencyMs;
    private final long timestampMillis;

    public ToolTraceStep(
            int step,
            String toolName,
            Map<String, String> arguments,
            String status,
            long latencyMs
    ) {
        this.step = step;
        this.toolName = toolName;
        this.arguments = arguments;
        this.status = status;
        this.latencyMs = latencyMs;
        this.timestampMillis = System.currentTimeMillis();
    }

    public int getStep() {
        return step;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public String getStatus() {
        return status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}

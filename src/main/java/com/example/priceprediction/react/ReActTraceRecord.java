package com.example.priceprediction.react;

import java.util.Map;

public class ReActTraceRecord {

    private final String traceId;
    private final String memoryId;
    private final int step;
    private final String toolName;
    private final Map<String, String> arguments;
    private final String status;
    private final ToolObservation observation;
    private final long latencyMs;
    private final long timestampMillis;

    public ReActTraceRecord(
            String traceId,
            String memoryId,
            int step,
            String toolName,
            Map<String, String> arguments,
            String status,
            ToolObservation observation,
            long latencyMs
    ) {
        this.traceId = traceId;
        this.memoryId = memoryId;
        this.step = step;
        this.toolName = toolName;
        this.arguments = arguments;
        this.status = status;
        this.observation = observation;
        this.latencyMs = latencyMs;
        this.timestampMillis = System.currentTimeMillis();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMemoryId() {
        return memoryId;
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

    public ToolObservation getObservation() {
        return observation;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }
}

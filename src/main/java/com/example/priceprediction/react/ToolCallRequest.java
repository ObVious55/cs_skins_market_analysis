package com.example.priceprediction.react;

import java.util.Collections;
import java.util.Map;

public class ToolCallRequest {

    private final String memoryId;
    private final String toolName;
    private final Map<String, String> arguments;

    public ToolCallRequest(String memoryId, String toolName, Map<String, String> arguments) {
        this.memoryId = memoryId;
        this.toolName = toolName;
        this.arguments = arguments == null ? Collections.emptyMap() : arguments;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }
}

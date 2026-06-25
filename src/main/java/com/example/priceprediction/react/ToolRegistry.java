package com.example.priceprediction.react;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolRegistry {

    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new RegisteredTool(
                "getUserInventory",
                "Read the logged-in user's local CS2 inventory.",
                List.of(new ToolParameterSchema("memoryId", true, false, "Current user memory id / steam id.")),
                false
        ));
        register(new RegisteredTool(
                "getItemPriceData",
                "Read real-time market price data by standard itemId.",
                List.of(new ToolParameterSchema("itemId", true, true, "Standard numeric itemId from cs_qaq_item_id.")),
                true
        ));
        register(new RegisteredTool(
                "runItemStrategyAnalysis",
                "Run K-line strategy analysis by standard itemId.",
                List.of(new ToolParameterSchema("itemId", true, true, "Standard numeric itemId from cs_qaq_item_id.")),
                true
        ));
    }

    public Optional<RegisteredTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<RegisteredTool> list() {
        return tools.values();
    }

    private void register(RegisteredTool tool) {
        tools.put(tool.getName(), tool);
    }
}

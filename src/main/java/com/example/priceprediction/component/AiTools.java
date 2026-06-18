package com.example.priceprediction.component;

import com.example.priceprediction.react.ReActToolExecutor;
import com.example.priceprediction.react.ToolCallRequest;
import com.example.priceprediction.react.ToolObservationFormatter;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiTools {

    private final ReActToolExecutor toolExecutor;
    private final ToolObservationFormatter observationFormatter;

    public AiTools(
            ReActToolExecutor toolExecutor,
            ToolObservationFormatter observationFormatter
    ) {
        this.toolExecutor = toolExecutor;
        this.observationFormatter = observationFormatter;
    }

    @Tool(name = "getUserInventory", value = "\u83b7\u53d6\u5f53\u524d\u767b\u5f55\u7528\u6237\u7684 CS2 \u9970\u54c1\u5e93\u5b58\uff0c\u8fd4\u56de\u9970\u54c1\u540d\u79f0\u3001\u78e8\u635f\u3001\u6570\u91cf\u7b49\u4fe1\u606f\u3002")
    public String getUserInventory(@ToolMemoryId String memoryId) {
        return observationFormatter.toJson(toolExecutor.execute(new ToolCallRequest(
                memoryId,
                "getUserInventory",
                Map.of("memoryId", memoryId == null ? "" : memoryId)
        )));
    }

    @Tool(name = "getItemPriceData", value = "\u6839\u636e\u9970\u54c1 itemId \u83b7\u53d6\u5b9e\u65f6\u4ef7\u683c\u6570\u636e\uff0c\u5305\u62ec\u5404\u5e73\u53f0\u4ef7\u683c\u3001\u6da8\u8dcc\u5e45\u3001\u5728\u552e\u6570\u91cf\u7b49\u3002\u5fc5\u987b\u4f20\u5165\u6570\u5b57 itemId\u3002")
    public String getItemPriceData(String itemId) {
        return observationFormatter.toJson(toolExecutor.execute(new ToolCallRequest(
                null,
                "getItemPriceData",
                Map.of("itemId", itemId == null ? "" : itemId)
        )));
    }

    @Tool(
            name = "runItemStrategyAnalysis",
            value = "\u6839\u636e\u9970\u54c1 itemId \u8fd0\u884c YAML \u7b56\u7565\u914d\u7f6e\u3001StrategyContext\u3001ta4j \u6307\u6807\u3001StrategySkill \u548c Aggregator \u805a\u5408\u95ed\u73af\uff0c\u8fd4\u56de\u7efc\u5408\u4e70\u5356\u4fe1\u53f7\u3001\u5f97\u5206\u3001\u98ce\u9669\u7b49\u7ea7\u3001\u6458\u8981\u548c\u5404\u7b56\u7565\u539f\u56e0\u3002\u5fc5\u987b\u4f20\u5165\u6570\u5b57 itemId\u3002"
    )
    public String runItemStrategyAnalysis(String itemId) {
        return observationFormatter.toJson(toolExecutor.execute(new ToolCallRequest(
                null,
                "runItemStrategyAnalysis",
                Map.of("itemId", itemId == null ? "" : itemId)
        )));
    }
}

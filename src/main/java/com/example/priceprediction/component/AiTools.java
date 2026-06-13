package com.example.priceprediction.component;

import com.example.priceprediction.dto.InventoryItemDTO;
import com.example.priceprediction.service.ApiDataService;
import com.example.priceprediction.service.InventoryService;
import com.example.priceprediction.strategy.core.StrategyAnalysisResult;
import com.example.priceprediction.strategy.engine.StrategyEngine;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AiTools {

    private final InventoryService inventoryService;
    private final ApiDataService apiDataService;
    private final StrategyEngine strategyEngine;

    public AiTools(
            InventoryService inventoryService,
            ApiDataService apiDataService,
            StrategyEngine strategyEngine
    ) {
        this.inventoryService = inventoryService;
        this.apiDataService = apiDataService;
        this.strategyEngine = strategyEngine;
    }

    @Tool(name = "getUserInventory", value = "\u83b7\u53d6\u5f53\u524d\u767b\u5f55\u7528\u6237\u7684 CS2 \u9970\u54c1\u5e93\u5b58\uff0c\u8fd4\u56de\u9970\u54c1\u540d\u79f0\u3001\u78e8\u635f\u3001\u6570\u91cf\u7b49\u4fe1\u606f\u3002")
    public String getUserInventory(@ToolMemoryId String memoryId) {
        if (memoryId == null || memoryId.isEmpty()) {
            return "\u8bf7\u5148\u767b\u5f55\u540e\u518d\u67e5\u8be2\u5e93\u5b58\u3002";
        }

        try {
            List<InventoryItemDTO> items = inventoryService.getUserInventoryFromDb(memoryId);
            if (items.isEmpty()) {
                return "\u5f53\u524d\u672c\u5730\u5e93\u5b58\u4e3a\u7a7a\u3002";
            }

            StringBuilder sb = new StringBuilder("\u5e93\u5b58\u5305\u542b\u4ee5\u4e0b\u9970\u54c1\uff1a\n");
            for (InventoryItemDTO item : items) {
                sb.append("- ").append(item.getName())
                        .append(" (").append(item.getWear()).append(") ")
                        .append("x").append(item.getAmount()).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "\u83b7\u53d6\u5e93\u5b58\u5931\u8d25: " + e.getMessage();
        }
    }

    @Tool(name = "getItemPriceData", value = "\u6839\u636e\u9970\u54c1 itemId \u83b7\u53d6\u5b9e\u65f6\u4ef7\u683c\u6570\u636e\uff0c\u5305\u62ec\u5404\u5e73\u53f0\u4ef7\u683c\u3001\u6da8\u8dcc\u5e45\u3001\u5728\u552e\u6570\u91cf\u7b49\u3002\u5fc5\u987b\u4f20\u5165\u6570\u5b57 itemId\u3002")
    public String getItemPriceData(String itemId) {
        try {
            JsonNode result = apiDataService.getPriceData(itemId);
            if (result != null && result.has("code") && result.get("code").asInt() == 200) {
                JsonNode goodsInfo = result.get("data").get("goods_info");

                StringBuilder sb = new StringBuilder("\u9970\u54c1\u4ef7\u683c\u6570\u636e\uff1a\n");
                sb.append("\u540d\u79f0: ").append(goodsInfo.path("market_hash_name").asText()).append("\n");
                sb.append("BUFF\u4ef7\u683c: CNY ").append(goodsInfo.path("buff_sell_price").asDouble(0)).append("\n");
                sb.append("\u60a0\u60a0\u6709\u54c1\u4ef7\u683c: CNY ").append(goodsInfo.path("yyyp_sell_price").asDouble(0)).append("\n");
                sb.append("Steam\u4ef7\u683c: CNY ").append(goodsInfo.path("steam_sell_price").asDouble(0)).append("\n");
                sb.append("1\u65e5\u6da8\u8dcc: ").append(goodsInfo.path("sell_price_rate_1").asDouble(0)).append("%\n");
                sb.append("7\u65e5\u6da8\u8dcc: ").append(goodsInfo.path("sell_price_rate_7").asDouble(0)).append("%\n");
                sb.append("30\u65e5\u6da8\u8dcc: ").append(goodsInfo.path("sell_price_rate_30").asDouble(0)).append("%\n");
                sb.append("BUFF\u5728\u552e\u6570\u91cf: ").append(goodsInfo.path("buff_sell_num").asInt(0)).append("\n");
                sb.append("\u60a0\u60a0\u6709\u54c1\u5728\u552e\u6570\u91cf: ").append(goodsInfo.path("yyyp_sell_num").asInt(0)).append("\n");
                sb.append("Steam\u5728\u552e\u6570\u91cf: ").append(goodsInfo.path("steam_sell_num").asInt(0)).append("\n");

                return sb.toString();
            }
            return "\u83b7\u53d6\u4ef7\u683c\u6570\u636e\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5 itemId \u662f\u5426\u6b63\u786e\u3002";
        } catch (Exception e) {
            return "\u67e5\u8be2\u4ef7\u683c\u5931\u8d25: " + e.getMessage();
        }
    }

    @Tool(
            name = "runItemStrategyAnalysis",
            value = "\u6839\u636e\u9970\u54c1 itemId \u8fd0\u884c YAML \u7b56\u7565\u914d\u7f6e\u3001StrategyContext\u3001ta4j \u6307\u6807\u3001StrategySkill \u548c Aggregator \u805a\u5408\u95ed\u73af\uff0c\u8fd4\u56de\u7efc\u5408\u4e70\u5356\u4fe1\u53f7\u3001\u5f97\u5206\u3001\u98ce\u9669\u7b49\u7ea7\u3001\u6458\u8981\u548c\u5404\u7b56\u7565\u539f\u56e0\u3002\u5fc5\u987b\u4f20\u5165\u6570\u5b57 itemId\u3002"
    )
    public StrategyAnalysisResult runItemStrategyAnalysis(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return StrategyAnalysisResult.builder()
                    .itemId(itemId)
                    .finalSignal("HOLD")
                    .finalScore(0)
                    .riskLevel("UNKNOWN")
                    .summary("itemId \u4e3a\u7a7a\uff0c\u65e0\u6cd5\u6267\u884c\u7b56\u7565\u5206\u6790\u3002")
                    .signals(Collections.emptyList())
                    .build();
        }

        try {
            return strategyEngine.analyze(itemId.trim());
        } catch (Exception e) {
            return StrategyAnalysisResult.builder()
                    .itemId(itemId)
                    .finalSignal("HOLD")
                    .finalScore(0)
                    .riskLevel("UNKNOWN")
                    .summary("\u7b56\u7565\u5206\u6790\u6267\u884c\u5931\u8d25: " + e.getMessage())
                    .signals(Collections.emptyList())
                    .build();
        }
    }
}

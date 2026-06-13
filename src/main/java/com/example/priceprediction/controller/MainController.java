package com.example.priceprediction.controller;

import com.example.priceprediction.service.ApiDataService;
import com.example.priceprediction.service.PredictionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MainController {

    private final ApiDataService dataService;
    private final PredictionService predictionService;
    private final ObjectMapper mapper = new ObjectMapper(); // 统一使用一个 mapper

    public MainController(ApiDataService dataService, PredictionService predictionService) {
        this.dataService = dataService;
        this.predictionService = predictionService;
    }

    // 1. 搜索接口修复
    @GetMapping(value = "/search-item", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> searchItem(@RequestParam(required = false) String query) {
        try {
            if (query == null || query.isBlank()) {
                return ResponseEntity.badRequest().body("{\"success\":false,\"error\":\"请提供搜索关键词\"}");
            }
            JsonNode response = dataService.searchItem(query);
            if (response != null && response.has("code") && response.get("code").asInt() == 200) {
                List<Map<String, String>> items = StreamSupport.stream(response.get("data").spliterator(), false)
                        .map(item -> Map.of(
                                "id", item.get("id").asText(),
                                "name", item.get("value").asText(),
                                "market_hash_name", item.get("value").asText()
                        )).collect(Collectors.toList());
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", items);
                return ResponseEntity.ok(mapper.writeValueAsString(result));
            }
            return ResponseEntity.badRequest().body("{\"success\":false,\"error\":\"搜索失败\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // 2. 价格详情接口修复 (最核心的 500 报错来源)
    @GetMapping(value = "/price-data/{itemId}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> getPriceData(@PathVariable String itemId) {
        try {
            JsonNode response = dataService.getPriceData(itemId);
            if (response != null && response.has("code") && response.get("code").asInt() == 200) {
                // 手动拼接，彻底解决 JsonNode 序列化为 {nodeType...} 的问题
                String jsonStr = "{\"success\":true,\"data\":" + response.toString() + "}";
                return ResponseEntity.ok(jsonStr);
            }
            return ResponseEntity.badRequest().body("{\"success\":false,\"error\":\"获取价格失败\"}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // 3. 销量/图表接口修复
    @GetMapping(value = "/sales-data/{itemId}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> getSalesData(@PathVariable String itemId, @RequestParam(defaultValue = "1") String platform) {
        try {
            Map<String, Object> data = dataService.getSalesData(itemId, platform);
            return ResponseEntity.ok(mapper.writeValueAsString(data));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"success\":false,\"error\":\"获取图表失败\"}");
        }
    }

    // 4. 热门饰品接口 (保持你的修复版，并增强健壮性)
    @GetMapping(value = "/hot-items", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> getHotItems() {
        try {
            JsonNode response = dataService.getHotItems();
            if (response != null && response.has("code") && response.get("code").asInt() == 200) {
                JsonNode itemsArray = response.get("data");
                return ResponseEntity.ok("{\"success\":true,\"data\":" + itemsArray.toString() + "}");
            }
            return ResponseEntity.ok("{\"success\":true,\"data\":[]}"); // 失败时返回空数组而不是报错，防止页面卡死
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"success\":false,\"data\":[]}");
        }
    }

    // 5. AI 预测接口 (保持当前逻辑)
    @PostMapping(value = "/predict-price", produces = "application/json;charset=UTF-8")
// 🌟 变化：把 JsonNode 换成 Map<String, Object>
    public ResponseEntity<?> predictPrice(@RequestBody Map<String, Object> bodyMap) {
        try {
            // 1. 将 Map 转为 JsonNode，以便兼容你现有的 Service 逻辑
            // 这里的转换是在内存中进行的，不会触发刚才那个反序列化异常
            JsonNode requestBody = mapper.valueToTree(bodyMap);

            // 2. 提取必要的 ID (从 Map 或生成的 Node 中提取均可)
            String steamId = requestBody.path("steam_user_id").asText();

            if (steamId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "steamId is missing"));
            }

            // 3. 调用 Service
            String prediction = predictionService.predictPrice(steamId, requestBody);

            // 4. 返回结果
            return ResponseEntity.ok(Map.of("success", true, "prediction", prediction));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }


//    K线数据
    @GetMapping(value = "/kline-data/{itemId}", produces = "application/json;charset=UTF-8")
    public ResponseEntity<?> getKlineData(@PathVariable String itemId, @RequestParam(defaultValue = "1day") String type) {
        try {
            JsonNode kline = dataService.getKlineData(itemId, type);
            if (kline != null) {
                return ResponseEntity.ok("{\"success\":true,\"data\":" + kline.toString() + "}");
            }
            return ResponseEntity.ok("{\"success\":false,\"data\":[]}");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"success\":false,\"error\":\"获取K线失败\"}");
        }
    }


}
package com.example.priceprediction.service;

import com.example.priceprediction.component.GlobalRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class ApiDataService {

    private final RestClient csqaqClient;
    private final RestClient csqaqKlineClient;
    private final GlobalRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public ApiDataService(
            @Value("${app.csqaq.base-url}") String csqaqBaseUrl,
            @Value("${app.csqaq.api-token}") String csqaqApiToken,
            @Value("${app.csqaq.kline-base-url}") String csqaqKlineBaseUrl,
            @Value("${app.csqaq.kline-api-token}") String csqaqKlineApiToken,
            GlobalRateLimiter rateLimiter
    ) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = new ObjectMapper();
        this.csqaqClient = RestClient.builder()
                .baseUrl(csqaqBaseUrl)
                .defaultHeader("ApiToken", csqaqApiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.csqaqKlineClient = RestClient.builder()
                .baseUrl(csqaqKlineBaseUrl)
                .defaultHeader("ApiToken", csqaqKlineApiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private JsonNode parseJson(String responseStr) {
        try {
            return objectMapper.readTree(responseStr);
        } catch (Exception e) {
            throw new RuntimeException("API response is not valid JSON: \n" + responseStr, e);
        }
    }

    public JsonNode searchItem(String query) {
        rateLimiter.acquire();
        String responseStr = csqaqClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search/suggest").queryParam("text", query).build())
                .retrieve()
                .body(String.class);
        return parseJson(responseStr);
    }

    public JsonNode getPriceData(String itemId) {
        rateLimiter.acquire();
        String responseStr = csqaqClient.get()
                .uri(uriBuilder -> uriBuilder.path("/info/good").queryParam("id", itemId).build())
                .retrieve()
                .body(String.class);
        return parseJson(responseStr);
    }

    public Map<String, Object> getSalesData(String itemId, String platform) {
        int id = Integer.parseInt(itemId);
        int plat = Integer.parseInt(platform != null ? platform : "1");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        try {
            rateLimiter.acquire();
            String sellNumStr = csqaqClient.post().uri("/info/chart")
                    .body(Map.of("good_id", id, "key", "sell_num", "platform", plat, "period", 30, "style", "all_style"))
                    .retrieve()
                    .body(String.class);
            JsonNode sellNumRes = parseJson(sellNumStr);
            if (sellNumRes != null && sellNumRes.path("code").asInt() == 200) {
                result.put("sell_num_data", sellNumRes.get("data"));
            }
        } catch (Exception e) {
            System.out.println("get sell_num failed: " + e.getMessage());
        }

        try {
            rateLimiter.acquire();
            String turnoverStr = csqaqClient.post().uri("/info/chart")
                    .body(Map.of("good_id", id, "key", "turnover_number", "platform", 3, "period", 30, "style", "all_style"))
                    .retrieve()
                    .body(String.class);
            JsonNode turnoverRes = parseJson(turnoverStr);
            if (turnoverRes != null && turnoverRes.path("code").asInt() == 200) {
                result.put("turnover_data", turnoverRes.get("data"));
            }
        } catch (Exception e) {
            System.out.println("get turnover_number failed: " + e.getMessage());
        }

        try {
            rateLimiter.acquire();
            String priceStr = csqaqClient.post().uri("/info/chart")
                    .body(Map.of("good_id", id, "key", "sell_price", "platform", plat, "period", 30, "style", "all_style"))
                    .retrieve()
                    .body(String.class);
            JsonNode priceRes = parseJson(priceStr);
            if (priceRes != null && priceRes.path("code").asInt() == 200) {
                result.put("price_data", priceRes.get("data"));
            }
        } catch (Exception e) {
            System.out.println("get sell_price failed: " + e.getMessage());
        }

        return result;
    }

    public JsonNode getHotItems() {
        rateLimiter.acquire();
        String responseStr = csqaqClient.post().uri("/info/get_series_list")
                .body(Map.of("page", 1, "page_size", 10))
                .retrieve()
                .body(String.class);
        return parseJson(responseStr);
    }

    public JsonNode getKlineData(String itemId, String periods) {
        return getKlineData(itemId, periods, 1);
    }

    public JsonNode getKlineData(String itemId, String periods, int plat) {
        if (itemId == null || itemId.trim().isEmpty() || !itemId.matches("\\d+")) {
            System.err.println("invalid numeric itemId: " + itemId);
            return objectMapper.createArrayNode();
        }

        rateLimiter.acquire();

        try {
            Map<String, Object> requestBody = Map.of(
                    "good_id", itemId.trim(),
                    "plat", plat,
                    "periods", normalizePeriods(periods),
                    "max_time", System.currentTimeMillis()
            );

            String responseStr = csqaqKlineClient.post()
                    .uri("/info/simple/chartAll")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractKlineArray(responseStr);
        } catch (Exception e) {
            System.err.println("get kline failed: " + e.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    private JsonNode extractKlineArray(String responseStr) {
        JsonNode root = parseJson(responseStr);
        if (root.has("code") && root.path("code").asInt(0) != 200) {
            System.err.println("CSQAQ kline error response: " + responseStr);
            return objectMapper.createArrayNode();
        }

        JsonNode dataNode = root.isArray() ? root : root.path("data");
        if (!dataNode.isArray()) {
            return objectMapper.createArrayNode();
        }

        return dataNode;
    }

    private String normalizePeriods(String periods) {
        if (periods == null || periods.isBlank()) {
            return "1day";
        }
        return periods;
    }
}

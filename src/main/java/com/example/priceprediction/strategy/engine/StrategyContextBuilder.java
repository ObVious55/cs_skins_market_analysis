package com.example.priceprediction.strategy.engine;

import com.example.priceprediction.service.ApiDataService;
import com.example.priceprediction.service.ItemKlineCacheService;
import com.example.priceprediction.strategy.core.StrategyContext;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class StrategyContextBuilder {

    private final ApiDataService apiDataService;
    private final ItemKlineCacheService itemKlineCacheService;

    public StrategyContextBuilder(ApiDataService apiDataService, ItemKlineCacheService itemKlineCacheService) {
        this.apiDataService = apiDataService;
        this.itemKlineCacheService = itemKlineCacheService;
    }

    public StrategyContext build(String itemId) {
        return build(itemId, null);
    }

    public StrategyContext build(String itemId, String marketHashName) {
        return build(
                itemId,
                marketHashName,
                Set.of(
                        "get_item_daily_history",
                        "analyze_item_trend",
                        "get_item_realtime_quote"
                )
        );
    }

    public StrategyContext build(String itemId, String marketHashName, Set<String> requiredTools) {
        if (requiredTools == null || requiredTools.isEmpty()) {
            requiredTools = Set.of(
                    "get_item_daily_history",
                    "analyze_item_trend",
                    "get_item_realtime_quote"
            );
        }

        StrategyContext.StrategyContextBuilder builder = StrategyContext.builder()
                .itemId(itemId)
                .marketHashName(marketHashName);

        List<StrategyContext.KlineBar> klines = new ArrayList<>();

        if (requiredTools.contains("get_item_daily_history")) {
            klines = fetchDailyKlines(itemId);
            builder.dailyKlines(klines);
            builder.turnoverData(fetchTurnoverData(itemId));
        }

        if (requiredTools.contains("analyze_item_trend")) {
            if (klines.isEmpty()) {
                klines = fetchDailyKlines(itemId);
                builder.dailyKlines(klines);
            }
            builder.trendStatus(analyzeTrendStatus(klines));
        }

        if (requiredTools.contains("get_item_realtime_quote")) {
            builder.priceSnapshot(fetchPriceSnapshot(itemId));
        }

        if (requiredTools.contains("search_item_news")) {
            builder.newsEvents(fetchNewsEvents(marketHashName));
        }

        return builder.build();
    }

    private List<StrategyContext.KlineBar> fetchDailyKlines(String itemId) {
        try {
            return itemKlineCacheService.getDailyKlines(itemId);
        } catch (Exception e) {
            log.warn("获取日K数据失败, itemId={}", itemId, e);
            return List.of();
        }
    }

    private List<StrategyContext.TurnoverPoint> fetchTurnoverData(String itemId) {
        try {
            return itemKlineCacheService.getDailyTurnover(itemId);
        } catch (Exception e) {
            log.warn("获取成交量数据失败, itemId={}", itemId, e);
            return List.of();
        }
    }

    private StrategyContext.PriceSnapshot fetchPriceSnapshot(String itemId) {
        try {
            JsonNode priceData = apiDataService.getPriceData(itemId);
            if (priceData == null) {
                log.warn("价格快照为空, itemId={}", itemId);
                return null;
            }

            JsonNode goodsInfo = priceData.path("data").path("goods_info");
            if (goodsInfo.isMissingNode() || goodsInfo.isNull()) {
                log.warn("价格快照缺少 data.goods_info, itemId={}", itemId);
                return null;
            }

            return StrategyContext.PriceSnapshot.builder()
                    .buffPrice(goodsInfo.path("buff_sell_price").asDouble(0.0))
                    .yyypPrice(goodsInfo.path("yyyp_sell_price").asDouble(0.0))
                    .steamPrice(goodsInfo.path("steam_sell_price").asDouble(0.0))
                    .buffSellNum(goodsInfo.path("buff_sell_num").asDouble(0.0))
                    .yyypSellNum(goodsInfo.path("yyyp_sell_num").asDouble(0.0))
                    .steamSellNum(goodsInfo.path("steam_sell_num").asDouble(0.0))
                    .turnoverNumber(goodsInfo.path("turnover_number").asDouble(0.0))
                    .build();
        } catch (Exception e) {
            log.warn("获取价格快照失败, itemId={}", itemId, e);
            return null;
        }
    }

    private String analyzeTrendStatus(List<StrategyContext.KlineBar> klines) {
        if (klines == null || klines.size() < 20) {
            return "UNKNOWN";
        }

        List<StrategyContext.KlineBar> sorted = klines.stream()
                .sorted(Comparator.comparingLong(StrategyContext.KlineBar::getTimestamp))
                .toList();

        double latestClose = sorted.get(sorted.size() - 1).getClose();
        double ma5 = sorted.subList(sorted.size() - 5, sorted.size())
                .stream()
                .mapToDouble(StrategyContext.KlineBar::getClose)
                .average()
                .orElse(latestClose);
        double ma20 = sorted.subList(sorted.size() - 20, sorted.size())
                .stream()
                .mapToDouble(StrategyContext.KlineBar::getClose)
                .average()
                .orElse(latestClose);

        if (latestClose < ma5 && ma5 < ma20) {
            return "STRONG_BEAR";
        }
        if (latestClose < ma20) {
            return "BEAR";
        }
        if (latestClose > ma5 && ma5 > ma20) {
            return "STRONG_BULL";
        }
        if (latestClose > ma20) {
            return "BULL";
        }
        return "SIDEWAYS";
    }

    private List<StrategyContext.NewsEvent> fetchNewsEvents(String marketHashName) {
        if (marketHashName == null || marketHashName.isBlank()) {
            return List.of();
        }
        return List.of();
    }
}

package com.example.priceprediction.service;

import com.example.priceprediction.entity.ItemKlineEntity;
import com.example.priceprediction.repository.ItemKlineRepository;
import com.example.priceprediction.strategy.core.StrategyContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ItemKlineCacheService {

    private static final int DEFAULT_PLAT = 1;
    private static final int DEFAULT_LIMIT = 120;
    private static final long TWO_DAYS_MILLIS = 2L * 24L * 60L * 60L * 1000L;

    private final ItemKlineRepository itemKlineRepository;
    private final ApiDataService apiDataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ItemKlineCacheService(ItemKlineRepository itemKlineRepository, ApiDataService apiDataService) {
        this.itemKlineRepository = itemKlineRepository;
        this.apiDataService = apiDataService;
    }

    @Transactional
    public List<StrategyContext.KlineBar> getDailyKlines(String itemId) {
        return getKlines(itemId, DEFAULT_PLAT, "1day", DEFAULT_LIMIT);
    }

    @Transactional
    public List<StrategyContext.KlineBar> getKlines(String itemId, int plat, String periods, int limit) {
        List<ItemKlineEntity> cached = loadCached(itemId, plat, periods, limit);
        if (hasEnoughFreshData(cached, limit)) {
            return toKlineBars(cached);
        }

        refreshFromApi(itemId, plat, periods);
        return toKlineBars(loadCached(itemId, plat, periods, limit));
    }

    public List<StrategyContext.TurnoverPoint> getDailyTurnover(String itemId) {
        return getDailyKlines(itemId).stream()
                .map(bar -> StrategyContext.TurnoverPoint.builder()
                        .timestamp(bar.getTimestamp())
                        .value(bar.getVolume())
                        .build())
                .toList();
    }

    private List<ItemKlineEntity> loadCached(String itemId, int plat, String periods, int limit) {
        List<ItemKlineEntity> rows = itemKlineRepository.findByGoodIdAndPlatAndPeriodsOrderByTimestampDesc(
                itemId,
                plat,
                periods,
                PageRequest.of(0, limit)
        );

        return rows.stream()
                .sorted(Comparator.comparing(ItemKlineEntity::getTimestamp))
                .toList();
    }

    private boolean hasEnoughFreshData(List<ItemKlineEntity> rows, int limit) {
        if (rows == null || rows.size() < Math.min(20, limit)) {
            return false;
        }

        ItemKlineEntity latest = rows.get(rows.size() - 1);
        Long timestamp = latest.getTimestamp();
        return timestamp != null && System.currentTimeMillis() - timestamp <= TWO_DAYS_MILLIS;
    }

    private void refreshFromApi(String itemId, int plat, String periods) {
        JsonNode klineData = apiDataService.getKlineData(itemId, periods, plat);
        JsonNode klineArray = unwrapArray(klineData);
        if (klineArray == null || !klineArray.isArray()) {
            return;
        }

        List<ParsedKline> klines = new ArrayList<>();
        for (JsonNode node : klineArray) {
            long timestamp = extractTimestamp(node);
            if (timestamp <= 0L) {
                continue;
            }

            klines.add(new ParsedKline(
                    timestamp,
                    extractPrice(node, "open"),
                    extractPrice(node, "high"),
                    extractPrice(node, "low"),
                    extractPrice(node, "close"),
                    extractVolume(node)
            ));
        }

        if (klines.isEmpty()) {
            return;
        }

        List<StrategyContext.TurnoverPoint> turnover = fetchTurnoverData(itemId);
        Map<Long, Double> turnoverByDay = indexTurnoverByDay(turnover);
        int turnoverOffset = Math.max(0, klines.size() - turnover.size());

        for (int i = 0; i < klines.size(); i++) {
            ParsedKline kline = klines.get(i);
            double mergedVolume = kline.volume();

            Double turnoverByTimestamp = turnoverByDay.get(dayKey(kline.timestamp()));
            if (turnoverByTimestamp != null && turnoverByTimestamp > 0.0) {
                mergedVolume = turnoverByTimestamp;
            } else {
                int turnoverIndex = i - turnoverOffset;
                if (turnoverIndex >= 0 && turnoverIndex < turnover.size()) {
                    double value = turnover.get(turnoverIndex).getValue();
                    if (value > 0.0) {
                        mergedVolume = value;
                    }
                }
            }

            saveOrUpdate(itemId, plat, periods, kline, mergedVolume);
        }
    }

    private void saveOrUpdate(String itemId, int plat, String periods, ParsedKline kline, double volume) {
        ItemKlineEntity entity = itemKlineRepository
                .findByGoodIdAndPlatAndPeriodsAndTimestamp(itemId, plat, periods, kline.timestamp())
                .orElseGet(ItemKlineEntity::new);

        entity.setGoodId(itemId);
        entity.setPlat(plat);
        entity.setPeriods(periods);
        entity.setTimestamp(kline.timestamp());
        entity.setOpenPrice(kline.open());
        entity.setHighPrice(kline.high());
        entity.setLowPrice(kline.low());
        entity.setClosePrice(kline.close());
        entity.setVolume(volume);

        itemKlineRepository.save(entity);
    }

    private List<StrategyContext.TurnoverPoint> fetchTurnoverData(String itemId) {
        List<StrategyContext.TurnoverPoint> result = new ArrayList<>();

        try {
            Map<String, Object> sales = apiDataService.getSalesData(itemId, String.valueOf(DEFAULT_PLAT));
            Object turnoverObj = sales == null ? null : sales.get("turnover_data");
            if (turnoverObj == null) {
                return result;
            }

            JsonNode turnoverNode = objectMapper.valueToTree(turnoverObj);
            JsonNode array = unwrapArray(turnoverNode);
            if (array == null || !array.isArray()) {
                return result;
            }

            for (JsonNode node : array) {
                long timestamp = extractTimestamp(node);
                double value = extractNumber(node);
                if (timestamp > 0L) {
                    result.add(StrategyContext.TurnoverPoint.builder()
                            .timestamp(timestamp)
                            .value(value)
                            .build());
                }
            }
        } catch (Exception ignored) {
            return result;
        }

        return result.stream()
                .sorted(Comparator.comparingLong(StrategyContext.TurnoverPoint::getTimestamp))
                .toList();
    }

    private List<StrategyContext.KlineBar> toKlineBars(List<ItemKlineEntity> rows) {
        return rows.stream()
                .map(row -> StrategyContext.KlineBar.builder()
                        .timestamp(row.getTimestamp())
                        .open(valueOrZero(row.getOpenPrice()))
                        .high(valueOrZero(row.getHighPrice()))
                        .low(valueOrZero(row.getLowPrice()))
                        .close(valueOrZero(row.getClosePrice()))
                        .volume(valueOrZero(row.getVolume()))
                        .build())
                .toList();
    }

    private Map<Long, Double> indexTurnoverByDay(List<StrategyContext.TurnoverPoint> turnover) {
        Map<Long, Double> result = new HashMap<>();
        for (StrategyContext.TurnoverPoint point : turnover) {
            if (point.getValue() > 0.0) {
                result.put(dayKey(point.getTimestamp()), point.getValue());
            }
        }
        return result;
    }

    private JsonNode unwrapArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.has("data") && node.get("data").isArray()) {
            return node.get("data");
        }
        if (node.has("data") && node.get("data").has("list") && node.get("data").get("list").isArray()) {
            return node.get("data").get("list");
        }
        if (node.has("list") && node.get("list").isArray()) {
            return node.get("list");
        }
        return node;
    }

    private long extractTimestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0L;
        }
        if (node.has("time")) {
            return normalizeTimestamp(node.path("time").asLong(0L));
        }
        if (node.has("timestamp")) {
            return normalizeTimestamp(node.path("timestamp").asLong(0L));
        }
        if (node.has("t")) {
            return normalizeTimestamp(node.path("t").asLong(0L));
        }
        if (node.isArray() && node.size() > 0) {
            return normalizeTimestamp(node.get(0).asLong(0L));
        }
        return 0L;
    }

    private double extractPrice(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        if (node.has(field)) {
            return node.path(field).asDouble(0.0);
        }
        if ("open".equals(field) && node.has("o")) {
            return node.path("o").asDouble(0.0);
        }
        if ("high".equals(field) && node.has("h")) {
            return node.path("h").asDouble(0.0);
        }
        if ("low".equals(field) && node.has("l")) {
            return node.path("l").asDouble(0.0);
        }
        if ("close".equals(field) && node.has("c")) {
            return node.path("c").asDouble(0.0);
        }
        return 0.0;
    }

    private double extractVolume(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        if (node.has("volume")) {
            return node.path("volume").asDouble(0.0);
        }
        if (node.has("vol")) {
            return node.path("vol").asDouble(0.0);
        }
        if (node.has("v")) {
            return node.path("v").asDouble(0.0);
        }
        return 0.0;
    }

    private double extractNumber(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        if (node.isNumber()) {
            return node.asDouble(0.0);
        }
        if (node.has("value")) {
            return node.path("value").asDouble(0.0);
        }
        if (node.has("y")) {
            return node.path("y").asDouble(0.0);
        }
        if (node.has("turnover_number")) {
            return node.path("turnover_number").asDouble(0.0);
        }
        if (node.has("volume")) {
            return node.path("volume").asDouble(0.0);
        }
        if (node.isArray() && node.size() > 0) {
            return node.get(node.size() - 1).asDouble(0.0);
        }
        return 0.0;
    }

    private long normalizeTimestamp(long timestamp) {
        if (timestamp > 0L && timestamp < 10_000_000_000L) {
            return timestamp * 1000L;
        }
        return timestamp;
    }

    private long dayKey(long timestamp) {
        return normalizeTimestamp(timestamp) / 86_400_000L;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private record ParsedKline(
            long timestamp,
            double open,
            double high,
            double low,
            double close,
            double volume
    ) {
    }
}

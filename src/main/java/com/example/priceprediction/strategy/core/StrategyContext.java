package com.example.priceprediction.strategy.core;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class StrategyContext {

    private String itemId;

    private String marketHashName;

    /**
     * 趋势状态：
     * STRONG_BEAR / BEAR / SIDEWAYS / BULL / STRONG_BULL / UNKNOWN
     */
    private String trendStatus;

    @Builder.Default
    private List<KlineBar> dailyKlines = new ArrayList<>();

    @Builder.Default
    private List<TurnoverPoint> turnoverData = new ArrayList<>();

    private PriceSnapshot priceSnapshot;

    @Builder.Default
    private List<NewsEvent> newsEvents = new ArrayList<>();

    @Data
    @Builder
    public static class KlineBar {
        private long timestamp;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;
    }

    @Data
    @Builder
    public static class TurnoverPoint {
        private long timestamp;
        private double value;
    }

    @Data
    @Builder
    public static class PriceSnapshot {
        private double buffPrice;
        private double yyypPrice;
        private double steamPrice;

        private double buffSellNum;
        private double yyypSellNum;
        private double steamSellNum;

        private double turnoverNumber;
    }

    @Data
    @Builder
    public static class NewsEvent {
        private long timestamp;
        private String title;
        private String summary;
        private String sentiment;
        private String source;
    }
}
package com.example.priceprediction.strategy.ta4j;

import com.example.priceprediction.strategy.core.StrategyContext;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;

import java.util.Comparator;
import java.util.List;

@Component
public class IndicatorCalculator {

    private final BarSeriesBuilder barSeriesBuilder;
    private final IndicatorFactory indicatorFactory;

    public IndicatorCalculator(BarSeriesBuilder barSeriesBuilder, IndicatorFactory indicatorFactory) {
        this.barSeriesBuilder = barSeriesBuilder;
        this.indicatorFactory = indicatorFactory;
    }

    public Snapshot calculate(StrategyContext context) {
        List<StrategyContext.KlineBar> klines = sortedKlines(context);
        BarSeries series = barSeriesBuilder.build(context.getItemId(), klines);

        int endIndex = series.getEndIndex();
        double sma5 = valueAt(indicatorFactory.sma(series, 5), endIndex);
        double sma20 = valueAt(indicatorFactory.sma(series, 20), endIndex);
        double rsi14 = valueAt(indicatorFactory.rsi(series, 14), endIndex);

        double latestVolume = effectiveVolume(context, klines.size() - 1);
        double previousAverageVolume = previousAverageVolume(context, klines, 5);
        double volumeRatio = previousAverageVolume <= 0.0 ? 0.0 : latestVolume / previousAverageVolume;

        return Snapshot.builder()
                .series(series)
                .sma5(sma5)
                .sma20(sma20)
                .rsi14(rsi14)
                .latestEffectiveVolume(latestVolume)
                .previousAverageVolume(previousAverageVolume)
                .volumeRatio(volumeRatio)
                .build();
    }

    public double previousAverageVolume(StrategyContext context, List<StrategyContext.KlineBar> klines, int days) {
        if (klines == null || klines.size() < days + 1) {
            return 0.0;
        }

        int endExclusive = klines.size() - 1;
        int start = Math.max(0, endExclusive - days);
        double sum = 0.0;
        int count = 0;

        for (int i = start; i < endExclusive; i++) {
            double value = effectiveVolume(context, i);
            if (value > 0.0) {
                sum += value;
                count++;
            }
        }

        return count == 0 ? 0.0 : sum / count;
    }

    public double effectiveVolume(StrategyContext context, int index) {
        if (context == null || index < 0) {
            return 0.0;
        }

        List<StrategyContext.KlineBar> klines = sortedKlines(context);
        if (index < klines.size() && klines.get(index).getVolume() > 0.0) {
            return klines.get(index).getVolume();
        }

        List<StrategyContext.TurnoverPoint> turnoverData = sortedTurnover(context);
        if (index < klines.size() && !turnoverData.isEmpty()) {
            double valueByTime = turnoverValueByTimestamp(turnoverData, klines.get(index).getTimestamp());
            if (valueByTime > 0.0) {
                return valueByTime;
            }

            int offset = klines.size() - turnoverData.size();
            int turnoverIndex = index - Math.max(offset, 0);
            if (turnoverIndex >= 0 && turnoverIndex < turnoverData.size()) {
                return turnoverData.get(turnoverIndex).getValue();
            }
        }

        StrategyContext.PriceSnapshot snapshot = context.getPriceSnapshot();
        if (index == klines.size() - 1 && snapshot != null) {
            return snapshot.getTurnoverNumber();
        }

        return 0.0;
    }

    private double turnoverValueByTimestamp(List<StrategyContext.TurnoverPoint> turnoverData, long timestamp) {
        if (timestamp <= 0L) {
            return 0.0;
        }

        long normalized = normalizeTimestamp(timestamp);
        return turnoverData.stream()
                .filter(point -> normalizeTimestamp(point.getTimestamp()) == normalized)
                .findFirst()
                .map(StrategyContext.TurnoverPoint::getValue)
                .orElse(0.0);
    }

    public List<StrategyContext.KlineBar> sortedKlines(StrategyContext context) {
        if (context == null || context.getDailyKlines() == null) {
            return List.of();
        }

        return context.getDailyKlines().stream()
                .sorted(Comparator.comparingLong(StrategyContext.KlineBar::getTimestamp))
                .toList();
    }

    private List<StrategyContext.TurnoverPoint> sortedTurnover(StrategyContext context) {
        if (context == null || context.getTurnoverData() == null) {
            return List.of();
        }

        return context.getTurnoverData().stream()
                .sorted(Comparator.comparingLong(StrategyContext.TurnoverPoint::getTimestamp))
                .toList();
    }

    private long normalizeTimestamp(long timestamp) {
        long millis = timestamp < 10_000_000_000L ? timestamp * 1000L : timestamp;
        return millis / 86_400_000L;
    }

    private double valueAt(SMAIndicator indicator, int index) {
        if (index < 0) {
            return 0.0;
        }
        return indicator.getValue(index).doubleValue();
    }

    private double valueAt(RSIIndicator indicator, int index) {
        if (index < 0) {
            return 0.0;
        }
        return indicator.getValue(index).doubleValue();
    }

    @Data
    @Builder
    public static class Snapshot {
        private BarSeries series;
        private double sma5;
        private double sma20;
        private double rsi14;
        private double latestEffectiveVolume;
        private double previousAverageVolume;
        private double volumeRatio;
    }
}

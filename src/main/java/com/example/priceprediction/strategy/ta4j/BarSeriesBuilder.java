package com.example.priceprediction.strategy.ta4j;

import com.example.priceprediction.strategy.core.StrategyContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class BarSeriesBuilder {

    public BarSeries build(String name, List<StrategyContext.KlineBar> klines) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(name == null ? "unknown" : name)
                .build();

        if (klines == null || klines.isEmpty()) {
            return series;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        for (int i = 0; i < klines.size(); i++) {
            StrategyContext.KlineBar k = klines.get(i);

            ZonedDateTime endTime = k.getTimestamp() > 0
                    ? ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(normalizeTimestamp(k.getTimestamp())),
                    ZoneId.systemDefault()
            )
                    : now.minusDays(klines.size() - i);

            series.addBar(
                    Duration.ofDays(1),
                    endTime,
                    series.numOf(k.getOpen()),
                    series.numOf(k.getHigh()),
                    series.numOf(k.getLow()),
                    series.numOf(k.getClose()),
                    series.numOf(k.getVolume())
            );
        }

        return series;
    }

    private long normalizeTimestamp(long timestamp) {
        if (timestamp < 10_000_000_000L) {
            return timestamp * 1000;
        }
        return timestamp;
    }
}

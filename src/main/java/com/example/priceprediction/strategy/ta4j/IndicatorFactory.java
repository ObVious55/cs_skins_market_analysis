package com.example.priceprediction.strategy.ta4j;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Component
public class IndicatorFactory {

    public ClosePriceIndicator close(BarSeries series) {
        return new ClosePriceIndicator(series);
    }

    public SMAIndicator sma(BarSeries series, int period) {
        return new SMAIndicator(close(series), period);
    }

    public EMAIndicator ema(BarSeries series, int period) {
        return new EMAIndicator(close(series), period);
    }

    public RSIIndicator rsi(BarSeries series, int period) {
        return new RSIIndicator(close(series), period);
    }
}
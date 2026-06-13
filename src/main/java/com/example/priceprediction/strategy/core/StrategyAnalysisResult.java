package com.example.priceprediction.strategy.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StrategyAnalysisResult {

    private String itemId;

    private String marketHashName;

    /**
     * BUY / SELL / HOLD / WATCH
     */
    private String finalSignal;

    private int finalScore;

    private String riskLevel;

    private String summary;

    private List<StrategySignal> signals;
}
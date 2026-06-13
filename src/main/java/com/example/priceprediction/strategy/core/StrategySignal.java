package com.example.priceprediction.strategy.core;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class StrategySignal {

    private String strategyName;

    private String displayName;

    /**
     * BUY / SELL / HOLD / WATCH
     */
    private String signal;

    /**
     * 单策略得分，建议范围：-100 ~ 100
     */
    private int score;

    /**
     * LOW / MEDIUM / HIGH / UNKNOWN
     */
    private String riskLevel;

    private String reason;

    private String buyReason;

    private String patternAnalysis;

    private Double stopLossPrice;

    @Builder.Default
    private Map<String, Object> details = new HashMap<>();

    public static StrategySignal hold(String strategyName, String displayName, String reason) {
        return StrategySignal.builder()
                .strategyName(strategyName)
                .displayName(displayName)
                .signal("HOLD")
                .score(0)
                .riskLevel("UNKNOWN")
                .reason(reason)
                .buyReason("")
                .patternAnalysis(reason)
                .details(new HashMap<>())
                .build();
    }
}
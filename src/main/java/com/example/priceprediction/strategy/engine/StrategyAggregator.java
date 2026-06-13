package com.example.priceprediction.strategy.engine;

import com.example.priceprediction.strategy.core.StrategyAnalysisResult;
import com.example.priceprediction.strategy.core.StrategyContext;
import com.example.priceprediction.strategy.core.StrategySignal;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class StrategyAggregator {

    public StrategyAnalysisResult aggregate(StrategyContext context, List<StrategySignal> signals) {
        List<StrategySignal> safeSignals = signals == null ? List.of() : signals;
        int finalScore = safeSignals.stream()
                .mapToInt(StrategySignal::getScore)
                .sum();

        String finalSignal = decideFinalSignal(finalScore, safeSignals);
        String riskLevel = decideRiskLevel(safeSignals);
        String summary = buildSummary(finalSignal, finalScore, safeSignals);

        return StrategyAnalysisResult.builder()
                .itemId(context.getItemId())
                .marketHashName(context.getMarketHashName())
                .finalSignal(finalSignal)
                .finalScore(finalScore)
                .riskLevel(riskLevel)
                .summary(summary)
                .signals(safeSignals)
                .build();
    }

    private String decideFinalSignal(int finalScore, List<StrategySignal> signals) {
        boolean hasSell = signals.stream().anyMatch(signal -> "SELL".equalsIgnoreCase(signal.getSignal()));
        if (hasSell) {
            return "SELL";
        }
        if (finalScore >= 18) {
            return "BUY";
        }
        if (finalScore >= 8) {
            return "WATCH";
        }
        return "HOLD";
    }

    private String decideRiskLevel(List<StrategySignal> signals) {
        boolean highRisk = signals.stream().anyMatch(signal -> "HIGH".equalsIgnoreCase(signal.getRiskLevel()));
        if (highRisk) {
            return "HIGH";
        }

        boolean mediumRisk = signals.stream().anyMatch(signal -> "MEDIUM".equalsIgnoreCase(signal.getRiskLevel()));
        if (mediumRisk) {
            return "MEDIUM";
        }

        return signals.isEmpty() ? "UNKNOWN" : "LOW";
    }

    private String buildSummary(String finalSignal, int finalScore, List<StrategySignal> signals) {
        StrategySignal strongest = signals.stream()
                .max(Comparator.comparingInt(StrategySignal::getScore))
                .orElse(null);

        if (strongest == null) {
            return "暂无可用策略信号，综合信号为 " + finalSignal + "，综合得分为 " + finalScore + "。";
        }

        return "综合策略信号为 " + finalSignal
                + "，综合得分为 " + finalScore
                + "。贡献最高策略: " + strongest.getDisplayName()
                + "，原因: " + strongest.getReason();
    }
}

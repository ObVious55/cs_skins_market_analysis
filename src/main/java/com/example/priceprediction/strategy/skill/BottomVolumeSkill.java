package com.example.priceprediction.strategy.skill;

import com.example.priceprediction.strategy.core.StrategyContext;
import com.example.priceprediction.strategy.core.StrategySignal;
import com.example.priceprediction.strategy.core.StrategySkill;
import com.example.priceprediction.strategy.ta4j.IndicatorCalculator;
import com.example.priceprediction.strategy.yaml.StrategyManager;
import com.example.priceprediction.strategy.yaml.StrategyYamlDefinition;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BottomVolumeSkill implements StrategySkill {

    private static final String STRATEGY_NAME = "bottom_volume";

    private final StrategyManager strategyManager;
    private final IndicatorCalculator indicatorCalculator;

    public BottomVolumeSkill(StrategyManager strategyManager, IndicatorCalculator indicatorCalculator) {
        this.strategyManager = strategyManager;
        this.indicatorCalculator = indicatorCalculator;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public String getDisplayName() {
        return strategyManager.get(STRATEGY_NAME)
                .map(StrategyYamlDefinition::getDisplayName)
                .orElse("CS饰品底部放量");
    }

    @Override
    public StrategySignal analyze(StrategyContext context) {
        StrategyYamlDefinition definition = strategyManager.get(STRATEGY_NAME).orElse(null);
        if (definition == null || definition.getExecutableRules() == null) {
            return StrategySignal.hold(getName(), getDisplayName(), "策略 YAML 未配置 executable_rules，暂不执行确定性判断");
        }

        List<StrategyContext.KlineBar> klines = indicatorCalculator.sortedKlines(context);
        StrategyYamlDefinition.ExecutableRules rules = definition.getExecutableRules();
        int minBars = requiredBarCount(rules);
        if (klines.size() < minBars) {
            return StrategySignal.hold(
                    getName(),
                    getDisplayName(),
                    "历史行情不足，至少需要 " + minBars + " 根日K，当前只有 " + klines.size() + " 根"
            );
        }

        IndicatorCalculator.Snapshot indicators = indicatorCalculator.calculate(context);
        RuleResult decline = evaluateDecline(context, klines, rules.getDeclineConfirm());
        RuleResult volume = evaluateVolume(indicators, rules.getVolumeSurge());
        int recentLowDays = rules.getDeclineConfirm() == null
                ? 7
                : valueOrDefault(rules.getDeclineConfirm().getRecentLowDays(), 7);

        RuleResult stabilize = evaluatePriceStabilize(
                klines,
                rules.getPriceStabilize(),
                recentLowDays
        );

        int score = score(definition, decline, volume, stabilize);
        String signal = decideSignal(score, decline, volume, stabilize);
        String riskLevel = decideRisk(signal, stabilize);
        Double stopLossPrice = stopLossPrice(definition, stabilize.referencePrice());

        Map<String, Object> details = new HashMap<>();
        details.put("decline_confirmed", decline.matched());
        details.put("decline_pct", decline.metric("decline_pct"));
        details.put("trend_status", context.getTrendStatus());
        details.put("volume_surge_confirmed", volume.matched());
        details.put("volume_ratio", volume.metric("volume_ratio"));
        details.put("latest_effective_volume", indicators.getLatestEffectiveVolume());
        details.put("previous_average_volume", indicators.getPreviousAverageVolume());
        details.put("price_stabilized", stabilize.matched());
        details.put("lower_shadow_ratio", stabilize.metric("lower_shadow_ratio"));
        details.put("break_recent_low", stabilize.metric("break_recent_low"));
        details.put("sma5", indicators.getSma5());
        details.put("sma20", indicators.getSma20());
        details.put("rsi14", indicators.getRsi14());
        details.put("max_position_ratio", maxPositionRatio(definition));

        String reason = buildReason(signal, decline, volume, stabilize, score);

        return StrategySignal.builder()
                .strategyName(getName())
                .displayName(getDisplayName())
                .signal(signal)
                .score(score)
                .riskLevel(riskLevel)
                .reason(reason)
                .buyReason("BUY".equals(signal) || "WATCH".equals(signal) ? reason : "")
                .patternAnalysis("底部放量规则: " + decline.reason() + "；" + volume.reason() + "；" + stabilize.reason())
                .stopLossPrice(stopLossPrice)
                .details(details)
                .build();
    }

    private RuleResult evaluateDecline(
            StrategyContext context,
            List<StrategyContext.KlineBar> klines,
            StrategyYamlDefinition.DeclineConfirm rule
    ) {
        StrategyYamlDefinition.DeclineConfirm config = rule == null
                ? new StrategyYamlDefinition.DeclineConfirm()
                : rule;

        int lookbackDays = valueOrDefault(config.getLookbackHighDays(), 20);
        int recentLowDays = valueOrDefault(config.getRecentLowDays(), 7);

        List<StrategyContext.KlineBar> lookback = tail(klines, lookbackDays);
        List<StrategyContext.KlineBar> recent = tail(klines, recentLowDays);

        double high = lookback.stream()
                .mapToDouble(StrategyContext.KlineBar::getHigh)
                .max()
                .orElse(0.0);
        double recentLow = recent.stream()
                .mapToDouble(StrategyContext.KlineBar::getLow)
                .min()
                .orElse(0.0);
        double declinePct = high <= 0.0 ? 0.0 : (high - recentLow) / high * 100.0;

        boolean trendAllowed = config.getAllowedTrendStatus() == null
                || config.getAllowedTrendStatus().isEmpty()
                || config.getAllowedTrendStatus().contains(context.getTrendStatus());
        boolean matched = trendAllowed && declinePct >= valueOrDefault(config.getMinDeclinePct(), 15.0);

        return RuleResult.builder()
                .matched(matched)
                .reason(matched
                        ? "已确认前期下跌，近端低点较回看高点回撤 " + round(declinePct) + "%"
                        : "下跌确认不足，回撤 " + round(declinePct) + "%，趋势状态 " + context.getTrendStatus())
                .metric("decline_pct", declinePct)
                .referencePrice(recentLow)
                .build();
    }

    private RuleResult evaluateVolume(
            IndicatorCalculator.Snapshot indicators,
            StrategyYamlDefinition.VolumeSurge rule
    ) {
        StrategyYamlDefinition.VolumeSurge config = rule == null
                ? new StrategyYamlDefinition.VolumeSurge()
                : rule;

        double minRatio = valueOrDefault(config.getMinVolumeRatio(), 3.0);
        double ratio = indicators.getVolumeRatio();
        boolean matched = ratio >= minRatio;

        return RuleResult.builder()
                .matched(matched)
                .reason(matched
                        ? "成交活跃度放大至 " + round(ratio) + " 倍"
                        : "成交活跃度未达阈值，当前 " + round(ratio) + " 倍")
                .metric("volume_ratio", ratio)
                .build();
    }

    private RuleResult evaluatePriceStabilize(
            List<StrategyContext.KlineBar> klines,
            StrategyYamlDefinition.PriceStabilize rule,
            int recentLowDays
    ) {
        StrategyYamlDefinition.PriceStabilize config = rule == null
                ? new StrategyYamlDefinition.PriceStabilize()
                : rule;

        StrategyContext.KlineBar latest = klines.get(klines.size() - 1);

        int start = Math.max(0, klines.size() - recentLowDays - 1);
        int end = klines.size() - 1;

        double previousRecentLow = klines.subList(start, end)
                .stream()
                .min(Comparator.comparingDouble(StrategyContext.KlineBar::getLow))
                .map(StrategyContext.KlineBar::getLow)
                .orElse(latest.getLow());

        boolean bullish = !Boolean.TRUE.equals(config.getRequireBullishCandle())
                || latest.getClose() > latest.getOpen();

        double maxBreakPct = valueOrDefault(config.getMaxBreakRecentLowPct(), 0.0);

        boolean breakRecentLow = previousRecentLow > 0.0
                && latest.getLow() < previousRecentLow * (1.0 - maxBreakPct / 100.0);

        double lowerShadowRatio = lowerShadowRatio(latest);

        boolean lowerShadowOk = lowerShadowRatio >= valueOrDefault(
                config.getMinLowerShadowRatio(),
                0.0
        );

        boolean matched = bullish && !breakRecentLow && lowerShadowOk;

        return RuleResult.builder()
                .matched(matched)
                .reason(matched
                        ? "价格企稳，未跌破近期低点且下影线承接比例 " + round(lowerShadowRatio)
                        : "价格企稳不足，阳线=" + bullish + "，破近期低点=" + breakRecentLow
                        + "，下影线比例=" + round(lowerShadowRatio))
                .metric("lower_shadow_ratio", lowerShadowRatio)
                .metric("break_recent_low", breakRecentLow)
                .referencePrice(Math.min(previousRecentLow, latest.getLow()))
                .build();
    }

    private int score(
            StrategyYamlDefinition definition,
            RuleResult decline,
            RuleResult volume,
            RuleResult stabilize
    ) {
        int score = definition.getScoreRules() == null
                ? 0
                : valueOrDefault(definition.getScoreRules().getBaseScore(), 0);

        if (decline.matched()) {
            score += adjustment(definition, "decline_confirmed", 5);
        }
        if (volume.matched()) {
            score += adjustment(definition, "volume_surge_confirmed", 8);
        }
        if (stabilize.matched()) {
            score += adjustment(definition, "price_stabilized", 5);
        }
        Object lowerShadow = stabilize.metric("lower_shadow_ratio");
        if (lowerShadow instanceof Double value && value >= 0.25) {
            score += adjustment(definition, "lower_shadow_support", 3);
        }
        Object breakRecentLow = stabilize.metric("break_recent_low");
        if (Boolean.TRUE.equals(breakRecentLow)) {
            score += adjustment(definition, "break_recent_low", -20);
        }

        return score;
    }

    private int adjustment(StrategyYamlDefinition definition, String name, int defaultDelta) {
        if (definition.getScoreRules() == null || definition.getScoreRules().getAdjustments() == null) {
            return defaultDelta;
        }

        return definition.getScoreRules().getAdjustments().stream()
                .filter(adjustment -> name.equals(adjustment.getName()))
                .findFirst()
                .map(StrategyYamlDefinition.ScoreAdjustment::getDelta)
                .orElse(defaultDelta);
    }

    private String decideSignal(int score, RuleResult decline, RuleResult volume, RuleResult stabilize) {
        if (Boolean.TRUE.equals(stabilize.metric("break_recent_low"))) {
            return "SELL";
        }
        if (decline.matched() && volume.matched() && stabilize.matched() && score >= 18) {
            return "BUY";
        }
        if (score >= 8) {
            return "WATCH";
        }
        return "HOLD";
    }

    private String decideRisk(String signal, RuleResult stabilize) {
        if ("SELL".equals(signal) || Boolean.TRUE.equals(stabilize.metric("break_recent_low"))) {
            return "HIGH";
        }
        if ("BUY".equals(signal) || "WATCH".equals(signal)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildReason(
            String signal,
            RuleResult decline,
            RuleResult volume,
            RuleResult stabilize,
            int score
    ) {
        return "底部放量信号=" + signal + "，得分=" + score
                + "。下跌确认=" + decline.matched()
                + "，放量确认=" + volume.matched()
                + "，价格企稳=" + stabilize.matched();
    }

    private Double stopLossPrice(StrategyYamlDefinition definition, double referencePrice) {
        if (referencePrice <= 0.0
                || definition.getRiskControl() == null
                || definition.getRiskControl().getStopLoss() == null) {
            return null;
        }

        double bufferPct = valueOrDefault(definition.getRiskControl().getStopLoss().getBufferPct(), 2.0);
        return round(referencePrice * (1.0 - bufferPct / 100.0));
    }

    private Double maxPositionRatio(StrategyYamlDefinition definition) {
        if (definition.getRiskControl() == null) {
            return null;
        }
        return definition.getRiskControl().getMaxPositionRatio();
    }

    private int requiredBarCount(StrategyYamlDefinition.ExecutableRules rules) {
        int lookbackHighDays = rules.getDeclineConfirm() == null
                ? 20
                : valueOrDefault(rules.getDeclineConfirm().getLookbackHighDays(), 20);

        int recentLowDays = rules.getDeclineConfirm() == null
                ? 7
                : valueOrDefault(rules.getDeclineConfirm().getRecentLowDays(), 7);

        int volumeDays = rules.getVolumeSurge() == null
                ? 6
                : valueOrDefault(rules.getVolumeSurge().getAvgVolumeDays(), 5) + 1;

        return Math.max(lookbackHighDays, Math.max(recentLowDays, volumeDays));
    }

    private List<StrategyContext.KlineBar> tail(List<StrategyContext.KlineBar> klines, int size) {
        return klines.subList(Math.max(0, klines.size() - size), klines.size());
    }

    private double lowerShadowRatio(StrategyContext.KlineBar bar) {
        double range = bar.getHigh() - bar.getLow();
        if (range <= 0.0) {
            return 0.0;
        }
        return (Math.min(bar.getOpen(), bar.getClose()) - bar.getLow()) / range;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RuleResult(
            boolean matched,
            String reason,
            Map<String, Object> metrics,
            double referencePrice
    ) {
        Object metric(String name) {
            return metrics.get(name);
        }

        static Builder builder() {
            return new Builder();
        }

        static class Builder {
            private boolean matched;
            private String reason;
            private final Map<String, Object> metrics = new HashMap<>();
            private double referencePrice;

            Builder matched(boolean matched) {
                this.matched = matched;
                return this;
            }

            Builder reason(String reason) {
                this.reason = reason;
                return this;
            }

            Builder metric(String name, Object value) {
                this.metrics.put(name, value);
                return this;
            }

            Builder referencePrice(double referencePrice) {
                this.referencePrice = referencePrice;
                return this;
            }

            RuleResult build() {
                return new RuleResult(matched, reason, metrics, referencePrice);
            }
        }
    }
}

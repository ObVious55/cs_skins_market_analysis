package com.example.priceprediction.strategy.engine;

import com.example.priceprediction.strategy.core.StrategyAnalysisResult;
import com.example.priceprediction.strategy.core.StrategyContext;
import com.example.priceprediction.strategy.core.StrategySignal;
import com.example.priceprediction.strategy.core.StrategySkill;
import com.example.priceprediction.strategy.yaml.StrategyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class StrategyEngine {

    private final List<StrategySkill> skills;
    private final StrategyContextBuilder contextBuilder;
    private final StrategyAggregator aggregator;
    private final StrategyManager strategyManager;

    public StrategyEngine(
            List<StrategySkill> skills,
            StrategyContextBuilder contextBuilder,
            StrategyAggregator aggregator,
            StrategyManager strategyManager
    ) {
        this.skills = skills;
        this.contextBuilder = contextBuilder;
        this.aggregator = aggregator;
        this.strategyManager = strategyManager;
    }

    public StrategyAnalysisResult analyze(String itemId, String marketHashName) {
        Set<String> activeStrategyNames = strategyManager.getActiveStrategyNames();
        Set<String> requiredTools = new LinkedHashSet<>(strategyManager.getRequiredTools());

        StrategyContext context = contextBuilder.build(itemId, marketHashName, requiredTools);
        List<StrategySignal> signals = new ArrayList<>();

        List<StrategySkill> activeSkills = skills.stream()
                .filter(skill -> activeStrategyNames.contains(skill.getName()))
                .sorted(Comparator.comparingInt(skill -> strategyManager.getPriority(skill.getName())))
                .toList();

        if (activeSkills.isEmpty()) {
            signals.add(StrategySignal.builder()
                    .strategyName("strategy_engine")
                    .displayName("策略引擎")
                    .signal("HOLD")
                    .score(0)
                    .riskLevel("UNKNOWN")
                    .reason("没有启用任何可执行策略，请检查 YAML 的 default_active 或补充对应 StrategySkill")
                    .build());

            return aggregator.aggregate(context, signals);
        }

        for (StrategySkill skill : activeSkills) {
            try {
                signals.add(skill.analyze(context));
            } catch (Exception e) {
                log.warn("策略执行失败: strategyName={}, itemId={}", skill.getName(), itemId, e);

                signals.add(StrategySignal.builder()
                        .strategyName(skill.getName())
                        .displayName(skill.getDisplayName())
                        .signal("HOLD")
                        .score(0)
                        .riskLevel("UNKNOWN")
                        .reason("策略执行失败: " + e.getMessage())
                        .build());
            }
        }

        return aggregator.aggregate(context, signals);
    }

    public StrategyAnalysisResult analyze(String itemId) {
        return analyze(itemId, null);
    }
}

package com.example.priceprediction.strategy.yaml;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StrategyManager {

    private final StrategyYamlLoader strategyYamlLoader;
    private final Map<String, StrategyYamlDefinition> strategies = new LinkedHashMap<>();

    public StrategyManager(StrategyYamlLoader strategyYamlLoader) {
        this.strategyYamlLoader = strategyYamlLoader;
    }

    @PostConstruct
    public void init() {
        strategies.clear();
        for (StrategyYamlDefinition definition : strategyYamlLoader.loadAll()) {
            strategies.put(definition.getName(), definition);
        }
    }

    public Set<String> getActiveStrategyNames() {
        return getActiveStrategies().stream()
                .map(StrategyYamlDefinition::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Collection<StrategyYamlDefinition> getActiveStrategies() {
        return strategies.values()
                .stream()
                .filter(strategy -> Boolean.TRUE.equals(strategy.getDefaultActive()))
                .toList();
    }

    public List<String> getRequiredTools() {
        return getActiveStrategies().stream()
                .flatMap(strategy -> strategy.getRequiredTools().stream())
                .distinct()
                .toList();
    }

    public int getPriority(String strategyName) {
        StrategyYamlDefinition definition = strategies.get(strategyName);
        if (definition == null || definition.getDefaultPriority() == null) {
            return 100;
        }
        return definition.getDefaultPriority();
    }

    public Optional<StrategyYamlDefinition> get(String strategyName) {
        return Optional.ofNullable(strategies.get(strategyName));
    }

    public Collection<StrategyYamlDefinition> getAll() {
        return strategies.values();
    }
}

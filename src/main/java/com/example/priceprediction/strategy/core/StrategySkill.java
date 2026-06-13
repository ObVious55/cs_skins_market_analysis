package com.example.priceprediction.strategy.core;

public interface StrategySkill {
     String getName();
     String getDisplayName();
     StrategySignal analyze(StrategyContext context);
}

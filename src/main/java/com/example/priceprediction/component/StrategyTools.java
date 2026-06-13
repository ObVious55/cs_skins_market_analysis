package com.example.priceprediction.component;

import com.example.priceprediction.strategy.core.StrategyAnalysisResult;
import com.example.priceprediction.strategy.engine.StrategyEngine;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class StrategyTools {

    private final StrategyEngine strategyEngine;

    public StrategyTools(StrategyEngine strategyEngine) {
        this.strategyEngine = strategyEngine;
    }

    @Tool(
            name = "runItemStrategyAnalysis",
            value = "根据饰品 itemId 运行所有已启用的策略分析，返回综合信号、评分、风险等级和各策略原因。"
    )
    public StrategyAnalysisResult runItemStrategyAnalysis(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return StrategyAnalysisResult.builder()
                    .itemId(itemId)
                    .finalSignal("HOLD")
                    .finalScore(0)
                    .riskLevel("UNKNOWN")
                    .summary("itemId 为空，无法执行策略分析。")
                    .signals(java.util.Collections.emptyList())
                    .build();
        }

        return strategyEngine.analyze(itemId);
    }
}
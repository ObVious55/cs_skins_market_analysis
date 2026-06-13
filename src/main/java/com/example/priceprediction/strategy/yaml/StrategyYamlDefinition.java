package com.example.priceprediction.strategy.yaml;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StrategyYamlDefinition {

    private String name;

    private String displayName;

    private String description;

    private String category = "trend";

    private List<Integer> coreRules = new ArrayList<>();

    private List<String> requiredTools = new ArrayList<>();

    private List<String> aliases = new ArrayList<>();

    private Boolean defaultActive = false;

    private Integer defaultPriority = 100;

    private List<String> marketRegimes = new ArrayList<>();

    private String instructions;

    /**
     * 可执行规则。
     * 注意：这里故意不初始化。
     * 如果 YAML 没写 executable_rules，说明该策略暂时不能被 Java 确定性执行。
     */
    private ExecutableRules executableRules;

    /**
     * 评分规则。
     * 可为空，Skill 可以使用默认评分逻辑。
     */
    private ScoreRules scoreRules;

    /**
     * 风险控制。
     * 可为空，Skill 可以使用默认风控逻辑。
     */
    private RiskControl riskControl;

    @Data
    public static class ExecutableRules {

        private DeclineConfirm declineConfirm;

        private VolumeSurge volumeSurge;

        private PriceStabilize priceStabilize;
    }

    @Data
    public static class DeclineConfirm {

        private Integer lookbackHighDays = 20;

        private Integer recentLowDays = 7;

        private Double minDeclinePct = 15.0;

        private List<String> allowedTrendStatus = new ArrayList<>();
    }

    @Data
    public static class VolumeSurge {

        private Integer avgVolumeDays = 5;

        private Double minVolumeRatio = 3.0;
    }

    @Data
    public static class PriceStabilize {

        private Boolean requireBullishCandle = false;

        private Double maxBreakRecentLowPct = 0.0;

        private Double minLowerShadowRatio = 0.0;
    }

    @Data
    public static class ScoreRules {

        private Integer baseScore = 0;

        private List<ScoreAdjustment> adjustments = new ArrayList<>();
    }

    @Data
    public static class ScoreAdjustment {

        private String name;

        private Integer delta = 0;
    }

    @Data
    public static class RiskControl {

        private Double maxPositionRatio = 0.0;

        private StopLoss stopLoss;
    }

    @Data
    public static class StopLoss {

        private String type = "";

        private Double bufferPct = 0.0;
    }
}
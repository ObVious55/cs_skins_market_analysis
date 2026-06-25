package com.example.priceprediction.harness;

import java.util.List;

public class AgentHarnessResult {

    private final String caseName;
    private final boolean passed;
    private final List<HarnessCheckResult> checks;

    public AgentHarnessResult(String caseName, List<HarnessCheckResult> checks) {
        this.caseName = caseName;
        this.checks = checks;
        this.passed = checks.stream().allMatch(HarnessCheckResult::isPassed);
    }

    public String getCaseName() {
        return caseName;
    }

    public boolean isPassed() {
        return passed;
    }

    public List<HarnessCheckResult> getChecks() {
        return checks;
    }
}

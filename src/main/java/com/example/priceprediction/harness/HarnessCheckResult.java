package com.example.priceprediction.harness;

public class HarnessCheckResult {

    private final String checkName;
    private final boolean passed;
    private final String message;

    public HarnessCheckResult(String checkName, boolean passed, String message) {
        this.checkName = checkName;
        this.passed = passed;
        this.message = message;
    }

    public String getCheckName() {
        return checkName;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}

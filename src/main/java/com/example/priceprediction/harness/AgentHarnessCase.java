package com.example.priceprediction.harness;

public class AgentHarnessCase {

    private final String name;
    private final String expectedItemId;
    private final String actualItemId;
    private final String expectedToolName;
    private final String actualToolName;
    private final double ragConfidence;
    private final String agentResponse;
    private final boolean hasSuccessfulPriceObservation;

    private AgentHarnessCase(Builder builder) {
        this.name = builder.name;
        this.expectedItemId = builder.expectedItemId;
        this.actualItemId = builder.actualItemId;
        this.expectedToolName = builder.expectedToolName;
        this.actualToolName = builder.actualToolName;
        this.ragConfidence = builder.ragConfidence;
        this.agentResponse = builder.agentResponse;
        this.hasSuccessfulPriceObservation = builder.hasSuccessfulPriceObservation;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public String getExpectedItemId() {
        return expectedItemId;
    }

    public String getActualItemId() {
        return actualItemId;
    }

    public String getExpectedToolName() {
        return expectedToolName;
    }

    public String getActualToolName() {
        return actualToolName;
    }

    public double getRagConfidence() {
        return ragConfidence;
    }

    public String getAgentResponse() {
        return agentResponse;
    }

    public boolean hasSuccessfulPriceObservation() {
        return hasSuccessfulPriceObservation;
    }

    public static class Builder {
        private final String name;
        private String expectedItemId;
        private String actualItemId;
        private String expectedToolName;
        private String actualToolName;
        private double ragConfidence = 1.0;
        private String agentResponse = "";
        private boolean hasSuccessfulPriceObservation;

        private Builder(String name) {
            this.name = name;
        }

        public Builder expectedItemId(String expectedItemId) {
            this.expectedItemId = expectedItemId;
            return this;
        }

        public Builder actualItemId(String actualItemId) {
            this.actualItemId = actualItemId;
            return this;
        }

        public Builder expectedToolName(String expectedToolName) {
            this.expectedToolName = expectedToolName;
            return this;
        }

        public Builder actualToolName(String actualToolName) {
            this.actualToolName = actualToolName;
            return this;
        }

        public Builder ragConfidence(double ragConfidence) {
            this.ragConfidence = ragConfidence;
            return this;
        }

        public Builder agentResponse(String agentResponse) {
            this.agentResponse = agentResponse;
            return this;
        }

        public Builder hasSuccessfulPriceObservation(boolean hasSuccessfulPriceObservation) {
            this.hasSuccessfulPriceObservation = hasSuccessfulPriceObservation;
            return this;
        }

        public AgentHarnessCase build() {
            return new AgentHarnessCase(this);
        }
    }
}

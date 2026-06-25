package com.example.priceprediction.service;

import java.util.Set;

public record AgentResponsePlan(
        boolean requiresConfirmation,
        String reply,
        String agentPrompt,
        Set<String> allowedTools
) {
    public static final String TOOL_GET_USER_INVENTORY = "getUserInventory";
    public static final String TOOL_GET_ITEM_PRICE_DATA = "getItemPriceData";
    public static final String TOOL_RUN_ITEM_STRATEGY_ANALYSIS = "runItemStrategyAnalysis";

    public static AgentResponsePlan askConfirmation(String reply) {
        return new AgentResponsePlan(true, reply, null, Set.of());
    }

    public static AgentResponsePlan continueWith(String agentPrompt) {
        return new AgentResponsePlan(false, null, agentPrompt, Set.of());
    }

    public static AgentResponsePlan continueWith(String agentPrompt, Set<String> allowedTools) {
        return new AgentResponsePlan(false, null, agentPrompt, allowedTools == null ? Set.of() : Set.copyOf(allowedTools));
    }

    public AgentResponsePlan withAllowedTools(Set<String> allowedTools) {
        return new AgentResponsePlan(
                requiresConfirmation,
                reply,
                agentPrompt,
                allowedTools == null ? Set.of() : Set.copyOf(allowedTools)
        );
    }
}

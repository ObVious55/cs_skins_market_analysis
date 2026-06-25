package com.example.priceprediction.harness;

import com.example.priceprediction.react.ReActTraceRecord;

import java.util.List;

public class RealAgentHarnessRunResult {

    private final RealAgentHarnessCase testCase;
    private final String traceId;
    private final String actualItemId;
    private final String actualItemName;
    private final String familyKey;
    private final String familyName;
    private final double ragConfidence;
    private final double topGap;
    private final boolean reliable;
    private final String agentResponse;
    private final boolean actualAskedConfirmation;
    private final List<ReActTraceRecord> traces;
    private final AgentHarnessResult evaluation;

    public RealAgentHarnessRunResult(
            RealAgentHarnessCase testCase,
            String traceId,
            String actualItemId,
            String actualItemName,
            String familyKey,
            String familyName,
            double ragConfidence,
            double topGap,
            boolean reliable,
            String agentResponse,
            boolean actualAskedConfirmation,
            List<ReActTraceRecord> traces,
            AgentHarnessResult evaluation
    ) {
        this.testCase = testCase;
        this.traceId = traceId;
        this.actualItemId = actualItemId;
        this.actualItemName = actualItemName;
        this.familyKey = familyKey;
        this.familyName = familyName;
        this.ragConfidence = ragConfidence;
        this.topGap = topGap;
        this.reliable = reliable;
        this.agentResponse = agentResponse;
        this.actualAskedConfirmation = actualAskedConfirmation;
        this.traces = traces;
        this.evaluation = evaluation;
    }

    public RealAgentHarnessCase getTestCase() {
        return testCase;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getActualItemId() {
        return actualItemId;
    }

    public String getActualItemName() {
        return actualItemName;
    }

    public String getFamilyKey() {
        return familyKey;
    }

    public String getFamilyName() {
        return familyName;
    }

    public double getRagConfidence() {
        return ragConfidence;
    }

    public double getTopGap() {
        return topGap;
    }

    public boolean isReliable() {
        return reliable;
    }

    public String getAgentResponse() {
        return agentResponse;
    }

    public boolean isActualAskedConfirmation() {
        return actualAskedConfirmation;
    }

    public List<ReActTraceRecord> getTraces() {
        return traces;
    }

    public AgentHarnessResult getEvaluation() {
        return evaluation;
    }
}

package com.example.priceprediction.harness;

import com.example.priceprediction.react.ReActTraceRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AgentHarnessEvaluator {

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(CNY|RMB|¥|￥|\\d+(\\.\\d+)?\\s*(CNY|RMB|元|块))",
            Pattern.CASE_INSENSITIVE
    );

    public AgentHarnessResult evaluate(AgentHarnessCase testCase) {
        List<HarnessCheckResult> checks = new ArrayList<>();
        checks.add(checkRagItem(testCase.getExpectedItemId(), testCase.getActualItemId()));
        checks.add(checkSingleToolCall(testCase.getExpectedToolName(), testCase.getActualToolName()));
        checks.add(checkLegacyLowConfidenceConfirmation(testCase));
        checks.add(checkLegacyNoFabricatedPrice(testCase));
        return new AgentHarnessResult(testCase.getName(), checks);
    }

    public AgentHarnessResult evaluateReal(
            RealAgentHarnessCase testCase,
            String actualItemId,
            boolean reliable,
            String agentResponse,
            boolean actualAskedConfirmation,
            List<ReActTraceRecord> traces
    ) {
        List<HarnessCheckResult> checks = new ArrayList<>();
        checks.add(checkRagItem(testCase.getExpectedItemId(), actualItemId));
        checks.add(checkToolCallSet(testCase.getExpectedTools(), traces));
        checks.add(checkRealConfirmationDecision(
                testCase.isShouldAskConfirmation(),
                reliable,
                actualAskedConfirmation,
                traces
        ));
        checks.add(checkRealNoFabricatedPrice(
                testCase.isAllowPriceInAnswer(),
                agentResponse,
                hasSuccessfulPriceObservation(traces)
        ));
        checks.add(checkNoDuplicateToolCall(traces));
        checks.add(checkNoMaxStepBlocked(traces));
        return new AgentHarnessResult(testCase.getCaseName(), checks);
    }

    private HarnessCheckResult checkRagItem(String expectedItemId, String actualItemId) {
        if (!StringUtils.hasText(expectedItemId)) {
            return pass("RAG_ITEM", "No expected itemId configured.");
        }
        boolean passed = Objects.equals(expectedItemId, actualItemId);
        return new HarnessCheckResult(
                "RAG_ITEM",
                passed,
                passed ? "Hybrid family query selected expected itemId."
                        : "Expected itemId " + expectedItemId + " but got " + actualItemId
        );
    }

    private HarnessCheckResult checkSingleToolCall(String expectedToolName, String actualToolName) {
        if (!StringUtils.hasText(expectedToolName)) {
            return pass("TOOL_CALL", "No expected tool configured.");
        }
        boolean passed = Objects.equals(expectedToolName, actualToolName);
        return new HarnessCheckResult(
                "TOOL_CALL",
                passed,
                passed ? "Agent called expected tool."
                        : "Expected tool " + expectedToolName + " but got " + actualToolName
        );
    }

    private HarnessCheckResult checkToolCallSet(List<String> expectedTools, List<ReActTraceRecord> traces) {
        Set<String> expected = new LinkedHashSet<>(expectedTools == null ? List.of() : expectedTools);
        Set<String> actual = new LinkedHashSet<>(
                traces == null ? List.of() : traces.stream().map(ReActTraceRecord::getToolName).toList()
        );
        boolean passed = Objects.equals(expected, actual);
        return new HarnessCheckResult(
                "TOOL_CALL",
                passed,
                passed ? "Agent called expected tool set."
                        : "Expected tools " + expected + " but got " + actual
        );
    }

    private HarnessCheckResult checkLegacyLowConfidenceConfirmation(AgentHarnessCase testCase) {
        boolean asksConfirmation = asksConfirmation(testCase.getAgentResponse());
        boolean noToolCall = !StringUtils.hasText(testCase.getActualToolName());
        boolean passed = testCase.getRagConfidence() >= 0.70 || (asksConfirmation && noToolCall);

        return new HarnessCheckResult(
                "LOW_CONFIDENCE_CONFIRMATION",
                passed,
                passed ? "Legacy low-confidence decision passed."
                        : "Low-confidence case should ask confirmation and avoid tool calls."
        );
    }

    private HarnessCheckResult checkRealConfirmationDecision(
            boolean shouldAskConfirmation,
            boolean reliable,
            boolean actualAskedConfirmation,
            List<ReActTraceRecord> traces
    ) {
        boolean noToolCall = traces == null || traces.isEmpty();
        if (shouldAskConfirmation) {
            boolean passed = actualAskedConfirmation && noToolCall;
            return new HarnessCheckResult(
                    "LOW_CONFIDENCE_CONFIRMATION",
                    passed,
                    passed ? "Expected confirmation was returned without tool calls."
                            : "Expected confirmation without tool calls, but response/tools did not match."
            );
        }

        boolean passed = !actualAskedConfirmation;
        return new HarnessCheckResult(
                "LOW_CONFIDENCE_CONFIRMATION",
                passed,
                passed ? "This case did not ask for confirmation."
                        : "Unexpected confirmation was returned. reliable=" + reliable
        );
    }

    private HarnessCheckResult checkLegacyNoFabricatedPrice(AgentHarnessCase testCase) {
        return checkRealNoFabricatedPrice(
                false,
                testCase.getAgentResponse(),
                testCase.hasSuccessfulPriceObservation()
        );
    }

    private HarnessCheckResult checkRealNoFabricatedPrice(
            boolean allowPriceInAnswer,
            String agentResponse,
            boolean hasSuccessfulPriceObservation
    ) {
        if (allowPriceInAnswer) {
            return pass("NO_FABRICATED_PRICE", "This case allows price in answer.");
        }

        boolean containsPrice = PRICE_PATTERN.matcher(safeText(agentResponse)).find();
        boolean passed = !containsPrice || hasSuccessfulPriceObservation;
        return new HarnessCheckResult(
                "NO_FABRICATED_PRICE",
                passed,
                passed ? "No unsupported price was found."
                        : "Response contains price-like content without successful price observation."
        );
    }

    private boolean hasSuccessfulPriceObservation(List<ReActTraceRecord> traces) {
        return traces != null && traces.stream()
                .anyMatch(trace -> "getItemPriceData".equals(trace.getToolName())
                        && "SUCCESS".equals(trace.getStatus()));
    }

    private HarnessCheckResult checkNoDuplicateToolCall(List<ReActTraceRecord> traces) {
        if (traces == null || traces.isEmpty()) {
            return pass("NO_DUPLICATE_TOOL_CALL", "No tool calls were made.");
        }

        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicated = new LinkedHashSet<>();
        for (ReActTraceRecord trace : traces) {
            if (!seen.add(trace.getToolName())) {
                duplicated.add(trace.getToolName());
            }
        }

        boolean passed = duplicated.isEmpty();
        return new HarnessCheckResult(
                "NO_DUPLICATE_TOOL_CALL",
                passed,
                passed ? "No duplicate tool calls in one turn."
                        : "Duplicate tool calls found: " + duplicated
        );
    }

    private HarnessCheckResult checkNoMaxStepBlocked(List<ReActTraceRecord> traces) {
        boolean blocked = traces != null && traces.stream()
                .anyMatch(trace -> "BLOCKED".equals(trace.getStatus()));
        return new HarnessCheckResult(
                "NO_MAX_STEP_BLOCKED",
                !blocked,
                blocked ? "Max step guard blocked at least one tool call."
                        : "Max step guard was not triggered."
        );
    }

    private boolean asksConfirmation(String response) {
        if (response == null) {
            return false;
        }
        String lower = response.toLowerCase();
        return response.contains("我不太确定你指的是哪一个饰品")
                || response.contains("我不太确定你指的是哪个饰品")
                || response.contains("先不直接查询价格或 K 线")
                || response.contains("目前最接近的候选")
                || response.contains("请你确认要分析的是")
                || lower.contains("please confirm");
    }

    private HarnessCheckResult pass(String checkName, String message) {
        return new HarnessCheckResult(checkName, true, message);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}

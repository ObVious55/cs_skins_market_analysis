package com.example.priceprediction.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentHarnessReportWriter {

    public Path writeMarkdown(List<AgentHarnessResult> results, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, buildMarkdown(results), StandardCharsets.UTF_8);
        return outputFile;
    }

    public Path writeRealMarkdown(List<RealAgentHarnessRunResult> results, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, buildRealMarkdown(results), StandardCharsets.UTF_8);
        return outputFile;
    }

    public String buildMarkdown(List<AgentHarnessResult> results) {
        Map<String, Metric> metrics = collectMetrics(results);
        long passedCases = results.stream().filter(AgentHarnessResult::isPassed).count();

        StringBuilder report = new StringBuilder();
        report.append("# Agent Harness Report\n\n");
        report.append("- Generated at: ").append(LocalDateTime.now()).append("\n");
        report.append("- Total cases: ").append(results.size()).append("\n");
        report.append("- Passed cases: ").append(passedCases).append("\n");
        report.append("- Failed cases: ").append(results.size() - passedCases).append("\n\n");

        report.append("## Metrics\n\n");
        report.append("| Metric | Passed | Total | Pass Rate |\n");
        report.append("|---|---:|---:|---:|\n");
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            Metric metric = entry.getValue();
            report.append("| ")
                    .append(toDisplayName(entry.getKey()))
                    .append(" | ")
                    .append(metric.passed)
                    .append(" | ")
                    .append(metric.total)
                    .append(" | ")
                    .append(String.format("%.2f%%", metric.passRate() * 100))
                    .append(" |\n");
        }

        report.append("\n## Case Details\n\n");
        report.append("| Case | Result | RAG Item | Tool Call | Low Confidence | No Fabricated Price |\n");
        report.append("|---|---|---|---|---|---|\n");
        for (AgentHarnessResult result : results) {
            report.append("| ")
                    .append(result.getCaseName())
                    .append(" | ")
                    .append(result.isPassed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(statusOf(result, "RAG_ITEM"))
                    .append(" | ")
                    .append(statusOf(result, "TOOL_CALL"))
                    .append(" | ")
                    .append(statusOf(result, "LOW_CONFIDENCE_CONFIRMATION"))
                    .append(" | ")
                    .append(statusOf(result, "NO_FABRICATED_PRICE"))
                    .append(" |\n");
        }

        report.append("\n## Failed Checks\n\n");
        boolean hasFailure = false;
        for (AgentHarnessResult result : results) {
            for (HarnessCheckResult check : result.getChecks()) {
                if (!check.isPassed()) {
                    hasFailure = true;
                    report.append("- ")
                            .append(result.getCaseName())
                            .append(" / ")
                            .append(check.getCheckName())
                            .append(": ")
                            .append(check.getMessage())
                            .append("\n");
                }
            }
        }
        if (!hasFailure) {
            report.append("- None\n");
        }

        return report.toString();
    }

    public String buildRealMarkdown(List<RealAgentHarnessRunResult> runResults) {
        List<AgentHarnessResult> evaluations = runResults.stream()
                .map(RealAgentHarnessRunResult::getEvaluation)
                .toList();
        Map<String, Metric> metrics = collectMetrics(evaluations);
        long passedCases = evaluations.stream().filter(AgentHarnessResult::isPassed).count();

        StringBuilder report = new StringBuilder();
        report.append("# Real Agent Harness Report\n\n");
        report.append("- Generated at: ").append(LocalDateTime.now()).append("\n");
        report.append("- Total cases: ").append(runResults.size()).append("\n");
        report.append("- Passed cases: ").append(passedCases).append("\n");
        report.append("- Failed cases: ").append(runResults.size() - passedCases).append("\n");
        report.append("- Source: real Hybrid Family recall + real Agent response + real ReAct tool traces\n\n");

        report.append("## Metrics\n\n");
        report.append("| Metric | Passed | Total | Pass Rate |\n");
        report.append("|---|---:|---:|---:|\n");
        for (Map.Entry<String, Metric> entry : metrics.entrySet()) {
            Metric metric = entry.getValue();
            report.append("| ")
                    .append(toDisplayName(entry.getKey()))
                    .append(" | ")
                    .append(metric.passed)
                    .append(" | ")
                    .append(metric.total)
                    .append(" | ")
                    .append(String.format("%.2f%%", metric.passRate() * 100))
                    .append(" |\n");
        }

        report.append("\n## Case Details\n\n");
        report.append("| Case | Query | Result | Expected Item | Selected Item | Family | Family Confidence | Reliable | Expected Tools | Actual Tools | TraceId |\n");
        report.append("|---|---|---|---|---|---|---:|---|---|---|---|\n");
        for (RealAgentHarnessRunResult result : runResults) {
            RealAgentHarnessCase testCase = result.getTestCase();
            report.append("| ")
                    .append(escape(testCase.getCaseName()))
                    .append(" | ")
                    .append(escape(testCase.getQuery()))
                    .append(" | ")
                    .append(result.getEvaluation().isPassed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(nullToDash(testCase.getExpectedItemId()))
                    .append(" | ")
                    .append(nullToDash(result.getActualItemId()))
                    .append(" | ")
                    .append(escape(familyLabel(result)))
                    .append(" | ")
                    .append(String.format("%.2f", result.getRagConfidence()))
                    .append(" | ")
                    .append(result.isReliable())
                    .append(" | ")
                    .append(escape(testCase.getExpectedTools().toString()))
                    .append(" | ")
                    .append(escape(actualTools(result).toString()))
                    .append(" | ")
                    .append(result.getTraceId())
                    .append(" |\n");
        }

        report.append("\n## Trace Summary\n\n");
        for (RealAgentHarnessRunResult result : runResults) {
            report.append("### ").append(result.getTestCase().getCaseName()).append("\n\n");
            report.append("- Family: ")
                    .append(nullToDash(familyLabel(result)))
                    .append("\n");
            report.append("- Selected item: ")
                    .append(nullToDash(result.getActualItemId()))
                    .append(" / ")
                    .append(nullToDash(result.getActualItemName()))
                    .append("\n");
            report.append("- Confidence: ")
                    .append(String.format("%.2f", result.getRagConfidence()))
                    .append(", topGap=")
                    .append(String.format("%.2f", result.getTopGap()))
                    .append(", reliable=")
                    .append(result.isReliable())
                    .append(", askedConfirmation=")
                    .append(result.isActualAskedConfirmation())
                    .append("\n");
            if (result.getTraces().isEmpty()) {
                report.append("- No tool call traces.\n\n");
                continue;
            }
            for (var trace : result.getTraces()) {
                report.append("- step=")
                        .append(trace.getStep())
                        .append(", tool=")
                        .append(trace.getToolName())
                        .append(", status=")
                        .append(trace.getStatus())
                        .append(", args=")
                        .append(trace.getArguments())
                        .append(", latencyMs=")
                        .append(trace.getLatencyMs())
                        .append("\n");
            }
            report.append("\n");
        }

        report.append("## Failed Checks\n\n");
        boolean hasFailure = false;
        for (RealAgentHarnessRunResult result : runResults) {
            for (HarnessCheckResult check : result.getEvaluation().getChecks()) {
                if (!check.isPassed()) {
                    hasFailure = true;
                    report.append("- ")
                            .append(result.getTestCase().getCaseName())
                            .append(" / ")
                            .append(check.getCheckName())
                            .append(": ")
                            .append(check.getMessage())
                            .append("\n");
                }
            }
        }
        if (!hasFailure) {
            report.append("- None\n");
        }

        return report.toString();
    }

    private Map<String, Metric> collectMetrics(List<AgentHarnessResult> results) {
        Map<String, Metric> metrics = new LinkedHashMap<>();
        for (AgentHarnessResult result : results) {
            for (HarnessCheckResult check : result.getChecks()) {
                Metric metric = metrics.computeIfAbsent(check.getCheckName(), ignored -> new Metric());
                metric.total++;
                if (check.isPassed()) {
                    metric.passed++;
                }
            }
        }
        return metrics;
    }

    private String statusOf(AgentHarnessResult result, String checkName) {
        return result.getChecks().stream()
                .filter(check -> check.getCheckName().equals(checkName))
                .findFirst()
                .map(check -> check.isPassed() ? "PASS" : "FAIL")
                .orElse("N/A");
    }

    private String toDisplayName(String checkName) {
        return switch (checkName) {
            case "RAG_ITEM" -> "RAG item accuracy";
            case "TOOL_CALL" -> "Tool call accuracy";
            case "LOW_CONFIDENCE_CONFIRMATION" -> "Low-confidence confirmation";
            case "NO_FABRICATED_PRICE" -> "No fabricated price";
            case "NO_DUPLICATE_TOOL_CALL" -> "No duplicate tool call";
            case "NO_MAX_STEP_BLOCKED" -> "No max-step block";
            default -> checkName;
        };
    }

    private Set<String> actualTools(RealAgentHarnessRunResult result) {
        return new LinkedHashSet<>(result.getTraces().stream()
                .map(trace -> trace.getToolName())
                .toList());
    }

    private String familyLabel(RealAgentHarnessRunResult result) {
        String familyKey = result.getFamilyKey();
        String familyName = result.getFamilyName();
        if ((familyKey == null || familyKey.isBlank()) && (familyName == null || familyName.isBlank())) {
            return null;
        }
        if (familyKey == null || familyKey.isBlank()) {
            return familyName;
        }
        if (familyName == null || familyName.isBlank()) {
            return familyKey;
        }
        return familyKey + " / " + familyName;
    }

    private String escape(String text) {
        return text == null ? "-" : text.replace("|", "\\|").replace("\n", " ");
    }

    private String nullToDash(String text) {
        return text == null || text.isBlank() ? "-" : escape(text);
    }

    private static class Metric {
        private int passed;
        private int total;

        private double passRate() {
            return total == 0 ? 0 : (double) passed / total;
        }
    }
}

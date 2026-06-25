package com.example.priceprediction.harness;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RealAgentHarnessReportExportTest {

    private static final Path JSONL_CASE_FILE = Path.of(
            "src",
            "test",
            "resources",
            "harness",
            "agent-harness-cases.jsonl"
    );

    private static final Path JSON_CASE_FILE = Path.of(
            "src",
            "test",
            "resources",
//            "harness",
            "agent-harness-cases.json"
    );

    @Autowired
    private RealAgentHarnessRunner runner;

    private final AgentHarnessReportWriter reportWriter = new AgentHarnessReportWriter();

    @Test
    void exportsRealBusinessHarnessReport() throws Exception {
        Path caseFile = resolveCaseFile();
        Assumptions.assumeTrue(
                Files.exists(caseFile),
                "Create src/test/resources/harness/agent-harness-cases.jsonl or agent-harness-cases.json to run real Agent Harness."
        );

        List<RealAgentHarnessRunResult> results = runner.runCases(caseFile);
        assertFalse(results.isEmpty(), "Harness case file must contain at least one case.");

        Path output = reportWriter.writeRealMarkdown(
                results,
                Path.of("target", "harness-report", "agent-harness-report.md")
        );

        String report = Files.readString(output);
        assertTrue(report.contains("# Real Agent Harness Report"));
        assertTrue(report.contains("Source: real RAG result + real Agent response + real ReAct tool traces"));
    }

    private Path resolveCaseFile() {
        if (Files.exists(JSONL_CASE_FILE)) {
            return JSONL_CASE_FILE;
        }
        return JSON_CASE_FILE;
    }
}

package com.example.priceprediction.harness;

import com.example.priceprediction.common.Result;
import com.example.priceprediction.controller.AiController;
import com.example.priceprediction.dto.ChatRequest;
import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.react.ReActTraceRecord;
import com.example.priceprediction.react.ReActTraceStore;
import com.example.priceprediction.react.ReActTurnContext;
import com.example.priceprediction.service.HybridFamilyRecallService;
import com.example.priceprediction.service.ItemFamilyRecallCandidate;
import com.example.priceprediction.service.ItemFamilyRecallResult;
import com.example.priceprediction.service.ItemFamilyVariantSelector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
public class RealAgentHarnessRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiController aiController;
    private final HybridFamilyRecallService hybridFamilyRecallService;
    private final ItemFamilyVariantSelector itemFamilyVariantSelector;
    private final ReActTurnContext turnContext;
    private final ReActTraceStore traceStore;
    private final AgentHarnessEvaluator evaluator;

    public RealAgentHarnessRunner(
            AiController aiController,
            HybridFamilyRecallService hybridFamilyRecallService,
            ItemFamilyVariantSelector itemFamilyVariantSelector,
            ReActTurnContext turnContext,
            ReActTraceStore traceStore,
            AgentHarnessEvaluator evaluator
    ) {
        this.aiController = aiController;
        this.hybridFamilyRecallService = hybridFamilyRecallService;
        this.itemFamilyVariantSelector = itemFamilyVariantSelector;
        this.turnContext = turnContext;
        this.traceStore = traceStore;
        this.evaluator = evaluator;
    }

    public List<RealAgentHarnessCase> loadCases(Path caseFile) throws IOException {
        if (!Files.exists(caseFile)) {
            return List.of();
        }

        if (caseFile.getFileName().toString().endsWith(".jsonl")) {
            return loadJsonlCases(caseFile);
        }

        return objectMapper.readValue(
                Files.readString(caseFile),
                new TypeReference<List<RealAgentHarnessCase>>() {
                }
        );
    }

    public List<RealAgentHarnessRunResult> runCases(Path caseFile) throws IOException {
        return loadCases(caseFile).stream()
                .map(this::runCase)
                .toList();
    }

    public RealAgentHarnessRunResult runCase(RealAgentHarnessCase testCase) {
        String traceId = UUID.randomUUID().toString();
        String memoryId = StringUtils.hasText(testCase.getMemoryId())
                ? testCase.getMemoryId()
                : "harness-" + traceId;

        traceStore.clear(traceId);

        ItemFamilyRecallResult familyRecall = hybridFamilyRecallService.recall(testCase.getQuery(), 10);
        ItemFamilyRecallCandidate primaryFamily = familyRecall == null ? null : familyRecall.getPrimary();
        String familyKey = primaryFamily == null ? null : primaryFamily.getFamilyKey();
        String familyName = primaryFamily == null ? null : primaryFamily.getName();
        double ragConfidence = familyRecall == null ? 0.0 : familyRecall.getConfidence();
        double topGap = familyRecall == null ? 0.0 : familyRecall.getTopGap();
        boolean reliable = familyRecall != null && familyRecall.isReliable();

        String actualItemId = null;
        String actualItemName = null;
        if (reliable && primaryFamily != null) {
            var selectedItem = itemFamilyVariantSelector.selectVariant(primaryFamily, testCase.getQuery());
            if (selectedItem.isPresent()) {
                CsQaqItemIdEntity item = selectedItem.get();
                actualItemId = String.valueOf(item.getItemId());
                actualItemName = item.getCnName();
            }
        }

        ChatRequest request = new ChatRequest();
        request.setSteamId(memoryId);
        request.setMessage(testCase.getQuery());
        request.setFollowUp(true);

        Result<String> response;
        turnContext.bind(memoryId, traceId);
        try {
            response = aiController.chatWithAi(request);
        } finally {
            turnContext.clear();
        }

        String agentResponse = response == null ? "" : response.getData();
        boolean actualAskedConfirmation = isConfirmationResponse(agentResponse);
        List<ReActTraceRecord> traces = traceStore.findByTraceId(traceId);
        AgentHarnessResult evaluation = evaluator.evaluateReal(
                testCase,
                actualItemId,
                reliable,
                agentResponse,
                actualAskedConfirmation,
                traces
        );

        return new RealAgentHarnessRunResult(
                testCase,
                traceId,
                actualItemId,
                actualItemName,
                familyKey,
                familyName,
                ragConfidence,
                topGap,
                reliable,
                agentResponse,
                actualAskedConfirmation,
                traces,
                evaluation
        );
    }

    private List<RealAgentHarnessCase> loadJsonlCases(Path caseFile) throws IOException {
        return Files.readAllLines(caseFile)
                .stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .map(this::readJsonLine)
                .toList();
    }

    private RealAgentHarnessCase readJsonLine(String line) {
        try {
            return objectMapper.readValue(line, RealAgentHarnessCase.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid harness JSONL line: " + line, e);
        }
    }

    private boolean isConfirmationResponse(String response) {
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
}

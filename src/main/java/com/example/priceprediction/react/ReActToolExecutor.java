package com.example.priceprediction.react;

import com.example.priceprediction.dto.InventoryItemDTO;
import com.example.priceprediction.service.ApiDataService;
import com.example.priceprediction.service.AgentTaskStateService;
import com.example.priceprediction.service.InventoryService;
import com.example.priceprediction.service.ToolObservationMemoryService;
import com.example.priceprediction.strategy.core.StrategyAnalysisResult;
import com.example.priceprediction.strategy.engine.StrategyEngine;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ReActToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolArgumentValidator validator;
    private final ReActExecutionGuard executionGuard;
    private final ReActTurnContext turnContext;
    private final ReActTraceStore traceStore;
    private final ToolTraceLogger traceLogger;
    private final ToolObservationMemoryService toolObservationMemoryService;
    private final AgentTaskStateService agentTaskStateService;
    private final InventoryService inventoryService;
    private final ApiDataService apiDataService;
    private final StrategyEngine strategyEngine;

    public ReActToolExecutor(
            ToolRegistry toolRegistry,
            ToolArgumentValidator validator,
            ReActExecutionGuard executionGuard,
            ReActTurnContext turnContext,
            ReActTraceStore traceStore,
            ToolTraceLogger traceLogger,
            ToolObservationMemoryService toolObservationMemoryService,
            AgentTaskStateService agentTaskStateService,
            InventoryService inventoryService,
            ApiDataService apiDataService,
            StrategyEngine strategyEngine
    ) {
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.executionGuard = executionGuard;
        this.turnContext = turnContext;
        this.traceStore = traceStore;
        this.traceLogger = traceLogger;
        this.toolObservationMemoryService = toolObservationMemoryService;
        this.agentTaskStateService = agentTaskStateService;
        this.inventoryService = inventoryService;
        this.apiDataService = apiDataService;
        this.strategyEngine = strategyEngine;
    }

    public ToolObservation execute(ToolCallRequest request) {
        long start = System.currentTimeMillis();
        String memoryId = effectiveMemoryId(request);
        ReActExecutionGuard.StepPermit permit = executionGuard.nextStep(memoryId);
        ToolObservation observation;

        if (!permit.allowed()) {
            observation = ToolObservation.blocked(
                    request.getToolName(),
                    "Max ReAct tool steps exceeded for this turn.",
                    permit.step()
            );
            logTrace(memoryId, request, observation, start);
            return observation;
        }

        Optional<RegisteredTool> registeredTool = toolRegistry.find(request.getToolName());
        if (registeredTool.isEmpty()) {
            observation = ToolObservation.validationError(
                    request.getToolName(),
                    "Unknown tool: " + request.getToolName(),
                    permit.step()
            );
            logTrace(memoryId, request, observation, start);
            return observation;
        }

        if (!turnContext.isToolAllowed(request.getToolName())) {
            observation = ToolObservation.blocked(
                    request.getToolName(),
                    "Tool is not allowed by current intent route. allowedTools="
                            + turnContext.currentAllowedTools(),
                    permit.step()
            );
            logTrace(memoryId, request, observation, start);
            return observation;
        }

        Optional<String> validationError = validator.validate(registeredTool.get(), request.getArguments());
        if (validationError.isPresent()) {
            observation = ToolObservation.validationError(
                    request.getToolName(),
                    validationError.get(),
                    permit.step()
            );
            logTrace(memoryId, request, observation, start);
            return observation;
        }

        if (registeredTool.get().isExternalDataRequired()) {
            Optional<String> confirmedItemError = agentTaskStateService.validateConfirmedItem(
                    memoryId,
                    request.getArguments().get("itemId")
            );
            if (confirmedItemError.isPresent()) {
                observation = ToolObservation.blocked(
                        request.getToolName(),
                        confirmedItemError.get(),
                        permit.step()
                );
                logTrace(memoryId, request, observation, start);
                return observation;
            }
        }

        try {
            observation = dispatch(request, permit.step());
        } catch (Exception e) {
            log.warn("Tool execution degraded, tool={}", request.getToolName(), e);
            observation = ToolObservation.degraded(
                    request.getToolName(),
                    e.getMessage(),
                    "工具执行失败，本轮回答只能说明数据暂时不可用，不能编造价格、成交量或 K 线结果。",
                    permit.step()
            );
        }

        logTrace(memoryId, request, observation, start);
        return observation;
    }

    private ToolObservation dispatch(ToolCallRequest request, int step) {
        return switch (request.getToolName()) {
            case "getUserInventory" -> getUserInventory(request, step);
            case "getItemPriceData" -> getItemPriceData(request, step);
            case "runItemStrategyAnalysis" -> runItemStrategyAnalysis(request, step);
            default -> ToolObservation.validationError(
                    request.getToolName(),
                    "Unknown tool: " + request.getToolName(),
                    step
            );
        };
    }

    private ToolObservation getUserInventory(ToolCallRequest request, int step) {
        String memoryId = effectiveMemoryId(request);
        List<InventoryItemDTO> items = inventoryService.getUserInventoryFromDb(memoryId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", items.size());
        data.put("items", items);
        return ToolObservation.success(request.getToolName(), data, step);
    }

    private ToolObservation getItemPriceData(ToolCallRequest request, int step) {
        String itemId = request.getArguments().get("itemId");
        JsonNode result = apiDataService.getPriceData(itemId);
        if (result == null || result.path("code").asInt(0) != 200) {
            return ToolObservation.degraded(
                    request.getToolName(),
                    "Price API returned non-success result.",
                    "价格数据暂时不可用，不能编造价格。",
                    step
            );
        }

        JsonNode goodsInfo = result.path("data").path("goods_info");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", itemId);
        data.put("marketHashName", goodsInfo.path("market_hash_name").asText(""));
        data.put("buffSellPrice", goodsInfo.path("buff_sell_price").asDouble(0));
        data.put("yyypSellPrice", goodsInfo.path("yyyp_sell_price").asDouble(0));
        data.put("steamSellPrice", goodsInfo.path("steam_sell_price").asDouble(0));
        data.put("sellPriceRate1d", goodsInfo.path("sell_price_rate_1").asDouble(0));
        data.put("sellPriceRate7d", goodsInfo.path("sell_price_rate_7").asDouble(0));
        data.put("sellPriceRate30d", goodsInfo.path("sell_price_rate_30").asDouble(0));
        data.put("buffSellNum", goodsInfo.path("buff_sell_num").asInt(0));
        data.put("yyypSellNum", goodsInfo.path("yyyp_sell_num").asInt(0));
        data.put("steamSellNum", goodsInfo.path("steam_sell_num").asInt(0));
        return ToolObservation.success(request.getToolName(), data, step);
    }

    private ToolObservation runItemStrategyAnalysis(ToolCallRequest request, int step) {
        String itemId = request.getArguments().get("itemId");
        StrategyAnalysisResult result = strategyEngine.analyze(itemId.trim());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", result.getItemId());
        data.put("marketHashName", result.getMarketHashName());
        data.put("finalSignal", result.getFinalSignal());
        data.put("finalScore", result.getFinalScore());
        data.put("riskLevel", result.getRiskLevel());
        data.put("summary", result.getSummary());
        data.put("signals", result.getSignals());
        return ToolObservation.success(request.getToolName(), data, step);
    }

    private void logTrace(String memoryId, ToolCallRequest request, ToolObservation observation, long start) {
        long latencyMs = System.currentTimeMillis() - start;
        traceStore.record(new ReActTraceRecord(
                turnContext.currentTraceId(),
                memoryId,
                observation.getStep(),
                request.getToolName(),
                request.getArguments(),
                observation.getStatus(),
                observation,
                latencyMs
        ));
        traceLogger.log(
                new ToolTraceStep(
                        observation.getStep(),
                        request.getToolName(),
                        request.getArguments(),
                        observation.getStatus(),
                        latencyMs
                ),
                observation
        );
        toolObservationMemoryService.remember(memoryId, request.getToolName(), observation, latencyMs);
        agentTaskStateService.updateFromToolObservation(memoryId, request.getToolName(), observation);
    }

    private String effectiveMemoryId(ToolCallRequest request) {
        if (request.getMemoryId() != null && !request.getMemoryId().isBlank()) {
            return request.getMemoryId();
        }
        return turnContext.currentMemoryId();
    }
}

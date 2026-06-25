package com.example.priceprediction.service;

import com.example.priceprediction.common.Result;
import com.example.priceprediction.config.RedisChatMemoryStore;
import com.example.priceprediction.dto.ChatRequest;
import com.example.priceprediction.react.ReActExecutionGuard;
import com.example.priceprediction.react.ReActTurnContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class AgentOrchestrator {

    private final InventoryAgent inventoryAgent;
    private final IntentClassifierService intentClassifierService;
    private final IntentRouter intentRouter;
    private final ItemResolver itemResolver;
    private final ReActExecutionGuard reActExecutionGuard;
    private final ReActTurnContext reActTurnContext;
    private final RedisChatMemoryStore redisChatMemoryStore;

    public AgentOrchestrator(
            InventoryAgent inventoryAgent,
            IntentClassifierService intentClassifierService,
            IntentRouter intentRouter,
            ItemResolver itemResolver,
            ReActExecutionGuard reActExecutionGuard,
            ReActTurnContext reActTurnContext,
            RedisChatMemoryStore redisChatMemoryStore
    ) {
        this.inventoryAgent = inventoryAgent;
        this.intentClassifierService = intentClassifierService;
        this.intentRouter = intentRouter;
        this.itemResolver = itemResolver;
        this.reActExecutionGuard = reActExecutionGuard;
        this.reActTurnContext = reActTurnContext;
        this.redisChatMemoryStore = redisChatMemoryStore;
    }

    public Result<String> chat(ChatRequest request) {
        String memoryId = request.getSteamId();
        String userMessage = request.getMessage();
        boolean isFollowUp = request.isFollowUp();

        log.info("AI chat request, user={}, followUp={}, message={}", memoryId, isFollowUp, userMessage);

        try {
            AgentResponsePlan plan = buildPlan(memoryId, userMessage, isFollowUp);
            if (plan.requiresConfirmation()) {
                log.info("Agent asks user to confirm before tool calling. message={}", userMessage);
                return Result.success(plan.reply());
            }

            reActExecutionGuard.beginTurn(memoryId);
            reActTurnContext.bind(memoryId, plan.allowedTools());
            String response;
            try {
                response = inventoryAgent.chat(memoryId, plan.agentPrompt());
                redisChatMemoryStore.compactConversationMemory(memoryId);
            } finally {
                reActTurnContext.clear();
            }
            return Result.success(response);
        } catch (Exception e) {
            log.error("Agent orchestration failed", e);
            return Result.error("AI 处理发生异常，请稍后再试");
        }
    }

    private AgentResponsePlan buildPlan(String memoryId, String userMessage, boolean isFollowUp) {
        log.info("Start agent orchestration, originalMessage={}", userMessage);

        IntentClassification intent = intentClassifierService.classify(userMessage, isFollowUp);
        Optional<AgentResponsePlan> routed = intentRouter.route(memoryId, userMessage, intent);
        if (routed.isPresent()) {
            return routed.get();
        }

        return itemResolver.resolve(memoryId, userMessage, intent);
    }
}

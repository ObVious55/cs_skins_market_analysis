package com.example.priceprediction.controller;

import com.example.priceprediction.common.Result;
import com.example.priceprediction.dto.ChatRequest;
import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.entity.ItemAliasMappingEntity;
import com.example.priceprediction.rag.ItemRagRetriever;
import com.example.priceprediction.rag.RefinedCandidate;
import com.example.priceprediction.rag.RefinementResult;
import com.example.priceprediction.react.ReActExecutionGuard;
import com.example.priceprediction.react.ReActTurnContext;
import com.example.priceprediction.service.CsQaqItemIdLookupService;
import com.example.priceprediction.service.InventoryAgent;
import com.example.priceprediction.service.ItemAliasLearningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final double RAG_CONFIRMATION_THRESHOLD = 0.70;
    private static final int CONFIRMATION_CANDIDATE_LIMIT = 3;

    private final InventoryAgent inventoryAgent;
    private final ItemRagRetriever itemRagRetriever;
    private final CsQaqItemIdLookupService csQaqItemIdLookupService;
    private final ItemAliasLearningService itemAliasLearningService;
    private final ReActExecutionGuard reActExecutionGuard;
    private final ReActTurnContext reActTurnContext;

    public AiController(InventoryAgent inventoryAgent,
                        ItemRagRetriever itemRagRetriever,
                        CsQaqItemIdLookupService csQaqItemIdLookupService,
                        ItemAliasLearningService itemAliasLearningService,
                        ReActExecutionGuard reActExecutionGuard,
                        ReActTurnContext reActTurnContext) {
        this.inventoryAgent = inventoryAgent;
        this.itemRagRetriever = itemRagRetriever;
        this.csQaqItemIdLookupService = csQaqItemIdLookupService;
        this.itemAliasLearningService = itemAliasLearningService;
        this.reActExecutionGuard = reActExecutionGuard;
        this.reActTurnContext = reActTurnContext;
    }

    @PostMapping("/chat")
    public Result<String> chatWithAi(@RequestBody ChatRequest request) {
        String memoryId = request.getSteamId();
        String userMessage = request.getMessage();
        boolean isFollowUp = request.isFollowUp();

        log.info("AI chat request, user={}, followUp={}, message={}", memoryId, isFollowUp, userMessage);

        try {
            RagOptimizationResult optimization = optimizeMessageByRag(memoryId, userMessage);
            if (optimization.requiresConfirmation()) {
                log.info("RAG confidence is low; ask user to confirm before tool calling. message={}", userMessage);
                return Result.success(optimization.reply());
            }
            reActExecutionGuard.beginTurn(memoryId);
            reActTurnContext.bind(memoryId);
            String response;
            try {
                response = inventoryAgent.chat(memoryId, optimization.agentPrompt());
            } finally {
                reActTurnContext.clear();
            }
            return Result.success(response);
        } catch (Exception e) {
            log.error("RAG optimization failed", e);
            return Result.error("RAG 优化发生异常，请稍后再试");
        }
    }

    private RagOptimizationResult optimizeMessageByRag(String memoryId, String userMessage) {
        try {
            log.info("Start RAG query optimization, originalMessage={}", userMessage);

            Optional<RagOptimizationResult> learnedAlias = tryLearnPendingAlias(memoryId, userMessage);
            if (learnedAlias.isPresent()) {
                return learnedAlias.get();
            }

            Optional<ItemAliasMappingEntity> aliasHit = itemAliasLearningService.findAliasForQuery(userMessage);
            if (aliasHit.isPresent()) {
                return buildAliasHitPrompt(userMessage, aliasHit.get());
            }

            RefinementResult refinement = itemRagRetriever.retrieveAndOptimize(userMessage, 50);
            if (refinement == null) {
                return RagOptimizationResult.continueWith(userMessage);
            }

            String ragPrimaryName = refinement.getPrimaryName();
            String ragPrimaryItemId = refinement.getPrimaryItemId();
            double confidence = refinement.getConfidence();
            log.info("RAG result, primaryItemId={}, primaryName={}, confidence={}",
                    ragPrimaryItemId, ragPrimaryName, confidence);

            if (ragPrimaryItemId == null || ragPrimaryItemId.isBlank()
                    || ragPrimaryName == null || ragPrimaryName.isBlank()) {
                return RagOptimizationResult.continueWith("""
                        用户原始问题：
                        %s

                        系统约束：
                        本次没有从饰品库中可靠识别到新的饰品。
                        如果用户说“这个”“这把”“它”，默认指当前对话中的饰品。
                        不允许编造不存在的饰品名称、item_id、价格或成交数据。
                        """.formatted(userMessage));
            }

            if (confidence < RAG_CONFIRMATION_THRESHOLD) {
                itemAliasLearningService.savePendingAlias(memoryId, userMessage);
                return RagOptimizationResult.askConfirmation(buildLowConfidenceConfirmation(refinement));
            }

            Optional<CsQaqItemIdEntity> qaqItemOpt =
                    csQaqItemIdLookupService.findByItemId(ragPrimaryItemId);

            if (qaqItemOpt.isEmpty()) {
                log.warn(
                        "RAG matched an item but cs_qaq_item_id mapping was not found, ragPrimaryName={}, confidence={}",
                        ragPrimaryName,
                        confidence
                );

                return RagOptimizationResult.continueWith("""
                        用户原始问题：
                        %s

                        RAG 初步识别结果：
                        - 初步饰品名称：%s
                        - 置信度：%.2f

                        系统已经尝试用该名称查询 cs_qaq_item_id，并在缺少磨损时尝试默认补全磨损，但仍未找到 item_id。

                        重要约束：
                        1. 可以使用 RAG 初步识别出的饰品名称回答。
                        2. 不允许编造 item_id、磨损版本、价格、成交量或 K 线数据。
                        3. 如果后续接口必须依赖 item_id，请说明当前缺少饰品 ID 映射。
                        """.formatted(userMessage, ragPrimaryName, confidence));
            }

            CsQaqItemIdEntity qaqItem = qaqItemOpt.get();
            Long finalItemId = qaqItem.getItemId();
            String finalCnName = qaqItem.getCnName();
            String finalMarketHashName = qaqItem.getMarketHashName();

            log.info(
                    "Final item matched, ragPrimaryName={}, itemId={}, cnName={}, marketHashName={}",
                    ragPrimaryName,
                    finalItemId,
                    finalCnName,
                    finalMarketHashName
            );

            return RagOptimizationResult.continueWith("""
                    用户原始问题：
                    %s

                    RAG 初步识别结果：
                    - 初步饰品名称：%s
                    - 置信度：%.2f

                    系统已通过 cs_qaq_item_id 表完成最终饰品匹配：
                    - 最终 item_id：%d
                    - 最终中文名：%s
                    - 最终 Steam market_hash_name：%s

                    重要约束：
                    1. 用户当前追问的饰品，必须以“最终饰品匹配结果”为准。
                    2. 如果后续调用 QAQ / 价格 / K 线接口，必须优先使用 item_id：%d。
                    3. 回答用户时，优先使用最终中文名：%s。
                    4. 不允许编造其它饰品、其它磨损、其它 item_id、价格、成交量或 K 线数据。
                    """.formatted(
                    userMessage,
                    ragPrimaryName,
                    confidence,
                    finalItemId,
                    finalCnName,
                    finalMarketHashName,
                    finalItemId,
                    finalCnName
            ));

        } catch (Exception e) {
            log.warn("RAG query optimization failed; continue with original message", e);
            return RagOptimizationResult.continueWith(userMessage);
        }
    }

    private Optional<RagOptimizationResult> tryLearnPendingAlias(String memoryId, String confirmationMessage) {
        Optional<String> pendingAlias = itemAliasLearningService.getPendingAlias(memoryId);
        if (pendingAlias.isEmpty()) {
            return Optional.empty();
        }

        Optional<CsQaqItemIdEntity> confirmedItem = resolveConfirmedItem(confirmationMessage);
        if (confirmedItem.isEmpty()) {
            return Optional.empty();
        }

        CsQaqItemIdEntity item = confirmedItem.get();
        Optional<ItemAliasMappingEntity> learnedAlias =
                itemAliasLearningService.learnAlias(pendingAlias.get(), item);
        itemAliasLearningService.clearPendingAlias(memoryId);

        if (learnedAlias.isEmpty()) {
            log.warn(
                    "Alias learning skipped because alias conflicts with another item, alias={}, itemId={}",
                    pendingAlias.get(),
                    item.getItemId()
            );
        } else {
            log.info(
                    "Alias learned, alias={}, itemId={}, cnName={}, marketHashName={}",
                    pendingAlias.get(),
                    item.getItemId(),
                    item.getCnName(),
                    item.getMarketHashName()
            );
        }

        return Optional.of(buildConfirmedAliasPrompt(confirmationMessage, pendingAlias.get(), item));
    }

    private Optional<CsQaqItemIdEntity> resolveConfirmedItem(String confirmationMessage) {
        Optional<CsQaqItemIdEntity> directMatch =
                csQaqItemIdLookupService.findByRagPrimaryName(confirmationMessage);
        if (directMatch.isPresent()) {
            return directMatch;
        }

        RefinementResult refinement = itemRagRetriever.retrieveAndOptimize(confirmationMessage, 50);
        if (refinement == null
                || refinement.getPrimaryName() == null
                || refinement.getPrimaryName().isBlank()
                || refinement.getConfidence() < RAG_CONFIRMATION_THRESHOLD) {
            return Optional.empty();
        }

        return csQaqItemIdLookupService.findByRagPrimaryName(refinement.getPrimaryName());
    }

    private RagOptimizationResult buildAliasHitPrompt(String userMessage, ItemAliasMappingEntity alias) {
        return RagOptimizationResult.continueWith("""
                用户原始问题：
                %s

                系统已从用户别名表命中标准饰品：
                - 用户别名：%s
                - 最终 item_id：%d
                - 最终中文名：%s
                - 最终 Steam market_hash_name：%s

                重要约束：
                1. 用户当前追问的饰品，必须以上面的别名映射结果为准。
                2. 如果后续调用 QAQ / 价格 / K 线接口，必须优先使用 item_id：%d。
                3. 回答用户时，优先使用最终中文名：%s。
                4. 不允许编造其它饰品、其它磨损、其它 item_id、价格、成交量或 K 线数据。
                """.formatted(
                userMessage,
                alias.getAlias(),
                alias.getItemId(),
                alias.getCnName(),
                alias.getMarketHashName(),
                alias.getItemId(),
                alias.getCnName()
        ));
    }

    private RagOptimizationResult buildConfirmedAliasPrompt(
            String confirmationMessage,
            String learnedAlias,
            CsQaqItemIdEntity item
    ) {
        return RagOptimizationResult.continueWith("""
                用户刚刚确认了一个饰品别名：
                - 用户原始别名：%s
                - 用户确认名称：%s

                系统已将该别名学习为标准饰品：
                - 最终 item_id：%d
                - 最终中文名：%s
                - 最终 Steam market_hash_name：%s

                重要约束：
                1. 本轮回答必须基于用户确认后的标准饰品。
                2. 如果后续调用 QAQ / 价格 / K 线接口，必须优先使用 item_id：%d。
                3. 后续其他用户再次使用该别名时，系统会优先命中 item_alias_mapping。
                4. 不允许编造其它饰品、其它磨损、其它 item_id、价格、成交量或 K 线数据。
                """.formatted(
                learnedAlias,
                confirmationMessage,
                item.getItemId(),
                item.getCnName(),
                item.getMarketHashName(),
                item.getItemId()
        ));
    }

    private String buildLowConfidenceConfirmation(RefinementResult refinement) {
        StringBuilder reply = new StringBuilder();
        reply.append("我不太确定你指的是哪个饰品，先不直接查询价格或 K 线，避免拿错数据。\n\n");
        reply.append("我目前最接近的候选是：\n");

        int count = 0;
        if (refinement.getCandidates() != null) {
            for (RefinedCandidate candidate : refinement.getCandidates()) {
                if (candidate == null || candidate.getName() == null || candidate.getName().isBlank()) {
                    continue;
                }
                count++;
                reply.append(count)
                        .append(". ")
                        .append(candidate.getName())
                        .append("（置信度 ")
                        .append(String.format("%.2f", Math.min(1.0, candidate.getScore())))
                        .append("）\n");
                if (count >= CONFIRMATION_CANDIDATE_LIMIT) {
                    break;
                }
            }
        }

        if (count == 0) {
            reply.append("- 暂时没有足够可靠的候选结果\n");
        }

        reply.append("\n请你确认要分析的是哪一个饰品，最好补充完整名称或磨损，例如“AK-47 | 红线（略有磨损）”。");
        return reply.toString();
    }

    private record RagOptimizationResult(
            boolean requiresConfirmation,
            String reply,
            String agentPrompt
    ) {
        private static RagOptimizationResult askConfirmation(String reply) {
            return new RagOptimizationResult(true, reply, null);
        }

        private static RagOptimizationResult continueWith(String agentPrompt) {
            return new RagOptimizationResult(false, null, agentPrompt);
        }
    }
}

package com.example.priceprediction.controller;

import com.example.priceprediction.common.Result;
import com.example.priceprediction.dto.ChatRequest;
import com.example.priceprediction.entity.CsQaqItemIdEntity;
import com.example.priceprediction.rag.ItemRagRetriever;
import com.example.priceprediction.rag.RefinementResult;
import com.example.priceprediction.service.CsQaqItemIdLookupService;
import com.example.priceprediction.service.InventoryAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final InventoryAgent inventoryAgent;
    private final ItemRagRetriever itemRagRetriever;
    private final CsQaqItemIdLookupService csQaqItemIdLookupService;

    public AiController(InventoryAgent inventoryAgent,
                        ItemRagRetriever itemRagRetriever,
                        CsQaqItemIdLookupService csQaqItemIdLookupService) {
        this.inventoryAgent = inventoryAgent;
        this.itemRagRetriever = itemRagRetriever;
        this.csQaqItemIdLookupService = csQaqItemIdLookupService;
    }

    @PostMapping("/chat")
    public Result<String> chatWithAi(@RequestBody ChatRequest request) {
        String memoryId = request.getSteamId();
        String userMessage = request.getMessage();
        boolean isFollowUp = request.isFollowUp();

        log.info("AI对话请求 - 用户: {}, 内容: {}, 是否追问: {}", memoryId, userMessage, isFollowUp);

        if (!isFollowUp) {
            try {
                String response = inventoryAgent.chat(memoryId, userMessage);
                return Result.success(response);
            } catch (Exception e) {
                log.error("AI 对话发生异常: ", e);
                return Result.error("AI 顾问暂时走神了，请稍后再试");
            }
        }

        try {
            String optimizedMessage = optimizeMessageByRag(userMessage);
            log.info("RAG优化后的问题：{}", optimizedMessage);

            String response = inventoryAgent.chat(memoryId, optimizedMessage);
            return Result.success(response);
        } catch (Exception e) {
            log.error("RAG 优化发生异常: ", e);
            return Result.error("RAG 优化发生异常，请稍后再试");
        }
    }

    private String optimizeMessageByRag(String userMessage) {
        try {
            log.info("开始执行 RAG Query 优化，原始问题: {}", userMessage);

            RefinementResult rr = itemRagRetriever.retrieveAndOptimize(userMessage, 50);

            if (rr == null) {
                log.info("RAG 返回为空，使用原始问题");
                return userMessage;
            }

            String ragPrimaryName = rr.getPrimaryName();
            double confidence = rr.getConfidence();

            log.info("RAG结果 - primaryName: {}, confidence: {}", ragPrimaryName, confidence);

            if (ragPrimaryName == null || ragPrimaryName.isBlank()) {
                return """
                        用户原始问题：
                        %s
                        
                        系统约束：
                        本次没有从饰品库中可靠识别到新的饰品。
                        如果用户说“这个”“这把”“它”，默认指当前对话中的饰品。
                        不允许编造不存在的饰品名称。
                        """.formatted(userMessage);
            }

            if (confidence < 0.6) {
                return """
                        用户原始问题：
                        %s
                        
                        RAG 检索结果置信度较低：
                        - 候选饰品：%s
                        - 置信度：%.2f
                        
                        系统约束：
                        这个 RAG 结果只能作为弱参考。
                        如果不确定用户说的是哪个饰品，请明确说明不确定。
                        不允许编造不存在的饰品名称。
                        """.formatted(
                        userMessage,
                        ragPrimaryName,
                        confidence
                );
            }

            /*
             * 关键点：
             * 这里不要在 Controller 里自己补磨损。
             *
             * findByRagPrimaryName 内部应该完成：
             * 1. 先用 ragPrimaryName 原名查 cs_qaq_item_id
             * 2. 如果查不到，并且 ragPrimaryName 没有磨损值，就补默认磨损再查
             * 3. 如果查到了，返回最终的 CsQaqItemIdEntity
             */
            Optional<CsQaqItemIdEntity> qaqItemOpt =
                    csQaqItemIdLookupService.findByRagPrimaryName(ragPrimaryName);

            if (qaqItemOpt.isEmpty()) {
                log.warn(
                        "RAG 识别到饰品，但 cs_qaq_item_id 未找到映射，ragPrimaryName: {}, confidence: {}",
                        ragPrimaryName,
                        confidence
                );

                return """
                        用户原始问题：
                        %s
                        
                        RAG 初步识别结果：
                        - 初步饰品名称：%s
                        - 置信度：%.2f
                        
                        系统已经尝试：
                        1. 先使用 RAG 初步饰品名称查询 cs_qaq_item_id。
                        2. 如果初步饰品名称没有磨损信息，则默认补全“崭新出厂 / Factory New”后再次查询。
                        
                        但是仍然没有在 cs_qaq_item_id 表中找到该饰品对应的 item_id。
                        
                        重要约束：
                        1. 可以使用 RAG 初步识别出的饰品名称回答。
                        2. 不允许编造 item_id。
                        3. 不允许让模型自行发明新的磨损版本。
                        4. 如果后续接口必须依赖 item_id，则说明当前缺少饰品 ID 映射。
                        5. 不允许编造价格、成交量、供需数据。
                        """.formatted(
                        userMessage,
                        ragPrimaryName,
                        confidence
                );
            }

            /*
             * 如果补磨损后查到了，qaqItem 就是 MySQL 里的最终饰品结果。
             * 例如：
             * ragPrimaryName = "USP-S | 枪响人亡"
             * 最终可能查到：
             * finalCnName = "USP-S | 枪响人亡 (崭新出厂)"
             * finalMarketHashName = "USP-S | Kill Confirmed (Factory New)"
             */
            CsQaqItemIdEntity qaqItem = qaqItemOpt.get();

            Long finalItemId = qaqItem.getItemId();
            String finalCnName = qaqItem.getCnName();
            String finalMarketHashName = qaqItem.getMarketHashName();

            log.info(
                    "饰品最终匹配成功 - RAG初步名称: {}, 最终itemId: {}, 最终中文名: {}, 最终marketHashName: {}",
                    ragPrimaryName,
                    finalItemId,
                    finalCnName,
                    finalMarketHashName
            );

            return """
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
                    2. 不要把 RAG 初步饰品名称当作最终饰品名称。
                    3. 如果 RAG 初步名称没有磨损信息，系统已经在查询服务中默认补全“崭新出厂 / Factory New”并重新查询 MySQL。
                    4. 后续如果调用 QAQ / 价格 / K线接口，必须优先使用 item_id：%d。
                    5. 回答用户时，优先使用最终中文名：%s。
                    6. 不允许编造其他饰品、其他磨损、其他 item_id。
                    7. 如果没有实时价格、成交量、K线数据，不要编造，只能说明数据不足。
                    """.formatted(
                    userMessage,
                    ragPrimaryName,
                    confidence,
                    finalItemId,
                    finalCnName,
                    finalMarketHashName,
                    finalItemId,
                    finalCnName
            );

        } catch (Exception e) {
            log.warn("RAG Query 优化失败，使用原始问题继续", e);
            return userMessage;
        }
    }
}
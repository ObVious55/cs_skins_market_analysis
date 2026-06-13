package com.example.priceprediction.service;

import com.example.priceprediction.rag.RefinementResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PredictionService {

    private final RestClient aiClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final com.example.priceprediction.rag.ItemRagRetriever itemRagRetriever;

    private static final int MAX_HISTORY_SIZE = 10;

    private static final long LOCK_LEASE_TIME = 120;
    private static final long LOCK_WAIT_TIME = 60;

    private static final double RAG_CONFIDENCE_THRESHOLD = 0.60;

    public PredictionService(
            @Value("${app.openai.base-url}") String baseUrl,
            @Value("${app.openai.api-key}") String apiKey,
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            com.example.priceprediction.rag.ItemRagRetriever itemRagRetriever) {

        this.objectMapper = new ObjectMapper();
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.itemRagRetriever = itemRagRetriever;

        this.aiClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String predictPrice(String steamId, JsonNode requestBody) {
        if (steamId == null || steamId.isBlank()) {
            throw new RuntimeException("steamId 不能为空");
        }

        String currentItemId = extractCurrentItemId(requestBody);
        String currentItemName = extractCurrentItemName(requestBody);

        boolean isNewAnalysis = requestBody.has("priceData");

        if (!isNewAnalysis && currentItemId.isBlank() && currentItemName.isBlank()) {
            throw new RuntimeException("追问阶段必须传入 currentItemId/itemId 或 itemName");
        }

        String sessionKey = buildSessionKey(steamId, currentItemId, currentItemName);
        String lockKey = "lock:" + sessionKey;

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);

            if (!locked) {
                throw new RuntimeException("无法获取锁，当前会话繁忙，请稍后重试");
            }

            List<Map<String, String>> messages;

            if (isNewAnalysis) {
                messages = handleNewAnalysis(
                        sessionKey,
                        currentItemId,
                        currentItemName,
                        requestBody
                );
            } else {
                messages = handleFollowUpQuestion(
                        sessionKey,
                        currentItemId,
                        currentItemName,
                        requestBody
                );
            }

            String aiAnswer = callAi(messages);

            messages.add(createMsg("assistant", aiAnswer));
            saveHistoryToRedis(sessionKey, messages);

            return aiAnswer;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("锁获取被中断");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI分析失败: " + e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 首次分析：
     * 前端已经明确选中了饰品，并传入 itemId/itemName/priceData。
     */
    private List<Map<String, String>> handleNewAnalysis(
            String sessionKey,
            String currentItemId,
            String currentItemName,
            JsonNode requestBody) {

        JsonNode priceData = requestBody.get("priceData");

        if (priceData == null || priceData.isNull()) {
            throw new RuntimeException("首次分析缺少 priceData");
        }

        redisTemplate.delete(sessionKey);

        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(createMsg(
                "system",
                """
                你是一个专业的 CS 饰品市场数据分析师。
                你必须基于用户提供的真实市场数据进行分析。
                不要编造没有提供的价格、成交量、供需数据。
                如果数据不足，必须明确说明。
                回答要客观、谨慎、分点说明。
                """
        ));

        String initialPrompt = buildDetailedPrompt(currentItemId, currentItemName, priceData);

        messages.add(createMsg("user", initialPrompt));

        return messages;
    }

    /**
     * 追问阶段：
     * 先用当前 itemId/itemName 找到历史会话。
     * 再用 RAG 分析用户 question。
     */
    private List<Map<String, String>> handleFollowUpQuestion(
            String sessionKey,
            String currentItemId,
            String currentItemName,
            JsonNode requestBody) {

        List<Map<String, String>> messages = getHistoryFromRedis(sessionKey);

        if (messages.isEmpty()) {
            throw new RuntimeException("会话已过期或不存在，请重新发起预测。");
        }

        String userQuestion = extractUserQuestion(requestBody);

        RagResolvedItem targetItem = resolveItemFromQuestion(userQuestion);

        FollowUpIntent intent = detectFollowUpIntent(userQuestion, targetItem);

        String optimizedQuestion = buildOptimizedFollowUpPrompt(
                currentItemId,
                currentItemName,
                userQuestion,
                targetItem,
                intent
        );

        messages.add(createMsg("user", optimizedQuestion));

        return messages;
    }

    /**
     * 用 RAG 从追问中识别用户是否提到了另一个饰品。
     */
    private RagResolvedItem resolveItemFromQuestion(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return RagResolvedItem.empty();
        }

        try {
            RefinementResult rr = itemRagRetriever.retrieveAndOptimize(userQuestion, 50);

            if (rr == null) {
                return RagResolvedItem.empty();
            }

            if (rr.getConfidence() < RAG_CONFIDENCE_THRESHOLD) {
                return RagResolvedItem.empty();
            }

            if (rr.getPrimaryName() == null || rr.getPrimaryName().isBlank()) {
                return RagResolvedItem.empty();
            }

            return new RagResolvedItem(
                    rr.getPrimaryItemId(),
                    rr.getPrimaryName(),
                    rr.getConfidence()
            );

        } catch (Exception e) {
            e.printStackTrace();
            return RagResolvedItem.empty();
        }
    }

    /**
     * 判断用户追问意图：
     * 1. CURRENT_ITEM：继续问当前饰品
     * 2. COMPARE_ITEM：和 RAG 识别到的饰品做对比
     * 3. SWITCH_ITEM：切换到 RAG 识别到的新饰品
     */
    private FollowUpIntent detectFollowUpIntent(String userQuestion, RagResolvedItem targetItem) {
        if (targetItem == null || !targetItem.exists()) {
            return FollowUpIntent.CURRENT_ITEM;
        }

        String q = userQuestion == null ? "" : userQuestion;

        if (containsAny(q,
                "比",
                "对比",
                "比较",
                "哪个",
                "哪一个",
                "更值得",
                "更适合",
                "和",
                "跟",
                "相比")) {
            return FollowUpIntent.COMPARE_ITEM;
        }

        if (containsAny(q,
                "分析",
                "看看",
                "查一下",
                "怎么样",
                "能买吗",
                "能不能买",
                "值得买吗",
                "适合买入吗")) {
            return FollowUpIntent.SWITCH_ITEM;
        }

        return FollowUpIntent.CURRENT_ITEM;
    }

    private String buildOptimizedFollowUpPrompt(
            String currentItemId,
            String currentItemName,
            String userQuestion,
            RagResolvedItem targetItem,
            FollowUpIntent intent) {

        if (intent == FollowUpIntent.COMPARE_ITEM && targetItem.exists()) {
            return """
                当前正在分析的饰品：
                - itemId：%s
                - itemName：%s

                用户原始追问：
                %s

                RAG 从用户追问中识别到另一个相关饰品：
                - targetItemId：%s
                - targetItemName：%s
                - confidence：%.2f

                用户意图判断：
                用户大概率想比较“当前饰品”和“目标饰品”。

                请按以下要求回答：
                1. 当前饰品仍然是当前会话主体，不要直接切换会话对象。
                2. 目标饰品只作为对比对象。
                3. 如果当前上下文中没有目标饰品的实时价格、供需、成交量数据，必须明确说明数据不足。
                4. 可以基于已知信息、饰品定位、流动性、市场热度做谨慎的定性比较。
                5. 不要编造目标饰品的具体价格、成交量和平台在售数量。
                """.formatted(
                    blankToNA(currentItemId),
                    blankToNA(currentItemName),
                    userQuestion,
                    blankToNA(targetItem.itemId),
                    blankToNA(targetItem.itemName),
                    targetItem.confidence
            );
        }

        if (intent == FollowUpIntent.SWITCH_ITEM && targetItem.exists()) {
            return """
                当前会话原本正在分析的饰品：
                - itemId：%s
                - itemName：%s

                用户原始追问：
                %s

                RAG 从用户追问中识别到用户可能想切换分析对象：
                - targetItemId：%s
                - targetItemName：%s
                - confidence：%.2f

                用户意图判断：
                用户大概率想从当前饰品切换到目标饰品。

                请按以下要求回答：
                1. 先明确说明：你识别到用户可能想分析 %s。
                2. 如果当前上下文中没有 %s 的实时价格、供需、成交量数据，不能直接给出完整投资分析。
                3. 不要编造价格数据。
                4. 可以提示用户先在前端搜索并选择该饰品，再进行完整 AI 分析。
                5. 如果用户只是想简单了解，可以给出非常谨慎的定性判断。
                """.formatted(
                    blankToNA(currentItemId),
                    blankToNA(currentItemName),
                    userQuestion,
                    blankToNA(targetItem.itemId),
                    blankToNA(targetItem.itemName),
                    targetItem.confidence,
                    blankToNA(targetItem.itemName),
                    blankToNA(targetItem.itemName)
            );
        }

        return """
            当前正在分析的饰品：
            - itemId：%s
            - itemName：%s

            用户追问：
            %s

            用户意图判断：
            用户大概率是在继续询问当前饰品。

            请结合当前对话历史继续回答。

            回答要求：
            1. 用户说“这个饰品”“这把”“它”时，默认指当前饰品。
            2. 如果用户询问短期走势，重点分析 1-7 天。
            3. 如果用户询问是否买入，要结合已有价格趋势、供需和平台价格。
            4. 不要编造当前对话中没有提供的数据。
            """.formatted(
                blankToNA(currentItemId),
                blankToNA(currentItemName),
                userQuestion
        );
    }

    private String buildDetailedPrompt(String currentItemId, String itemName, JsonNode priceData) {
        JsonNode itemInfo = priceData.path("data").path("goods_info");

        if (itemInfo.isMissingNode() || itemInfo.isNull()) {
            throw new RuntimeException("priceData 缺少 data.goods_info");
        }

        String realItemName = itemInfo.path("market_hash_name").asText("");

        if (realItemName == null || realItemName.isBlank()) {
            realItemName = itemName;
        }

        return """
            你是一位顶级的 CS 饰品市场数据分析师。请根据以下提供的实时数据和历史价格，对饰品进行一次全面、深入的投资分析。

            当前分析饰品：
            - itemId：%s
            - itemName：%s

            1. 历史价格参考：
            - 1日涨跌：%.2f%%
            - 7日涨跌：%.2f%%
            - 30日涨跌：%.2f%%

            2. 当前市场快照（价格）：
            - BUFF 售价：¥%s
            - 悠悠有品售价：¥%s
            - Steam 售价：¥%s

            3. 当前供需数据：
            - BUFF 在售数量：%s
            - 悠悠有品在售数量：%s
            - Steam 在售数量：%s
            - Steam 市场最近日成交量：%s

            请基于以上所有信息，进行分析并回答以下问题：
            1. 价格趋势与供需分析
            2. 平台选择建议（计算手续费：悠悠有品 1%%，BUFF 2.5%%，Steam 15%%）
            3. 短期（1-7天）价格预测
            4. 中期（8-15天）价格预测
            5. 长期（15天以上）价格预测
            6. 投资建议

            重要格式要求：
            - 使用简洁的文字格式回答，不要使用 markdown 语法
            - 每个问题的回答用数字序号开头，如“1. ”
            - 段落之间用空行分隔
            - 不要编造没有提供的数据
            """.formatted(
                blankToNA(currentItemId),
                blankToNA(realItemName),
                itemInfo.path("sell_price_rate_1").asDouble(0),
                itemInfo.path("sell_price_rate_7").asDouble(0),
                itemInfo.path("sell_price_rate_30").asDouble(0),
                itemInfo.path("buff_sell_price").asText("N/A"),
                itemInfo.path("yyyp_sell_price").asText("N/A"),
                itemInfo.path("steam_sell_price").asText("N/A"),
                itemInfo.path("buff_sell_num").asText("N/A"),
                itemInfo.path("yyyp_sell_num").asText("N/A"),
                itemInfo.path("steam_sell_num").asText("N/A"),
                itemInfo.path("turnover_number").asText("N/A")
        );
    }

    private String callAi(List<Map<String, String>> messages) throws Exception {
        Map<String, Object> aiReq = new HashMap<>();
        aiReq.put("model", "deepseek-chat");
        aiReq.put("messages", messages);

        String responseStr = aiClient.post()
                .uri("/chat/completions")
                .body(aiReq)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseStr);

        String aiAnswer = root
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");

        if (aiAnswer == null || aiAnswer.isBlank()) {
            throw new RuntimeException("AI 返回内容为空");
        }

        return aiAnswer;
    }

    private String extractCurrentItemId(JsonNode requestBody) {
        String itemId = requestBody.path("itemId").asText("");

        if (itemId == null || itemId.isBlank()) {
            itemId = requestBody.path("currentItemId").asText("");
        }

        if (itemId == null || itemId.isBlank()) {
            itemId = requestBody.path("id").asText("");
        }

        return itemId == null ? "" : itemId.trim();
    }

    private String extractCurrentItemName(JsonNode requestBody) {
        String itemName = requestBody.path("itemName").asText("");

        if (itemName == null || itemName.isBlank()) {
            itemName = requestBody.path("currentItemName").asText("");
        }

        return itemName == null ? "" : itemName.trim();
    }

    private String extractUserQuestion(JsonNode requestBody) {
        String question = requestBody.path("question").asText("");

        if (question == null || question.isBlank()) {
            question = requestBody.path("message").asText("");
        }

        if (question == null || question.isBlank()) {
            question = requestBody.path("userQuery").asText("");
        }

        if (question == null || question.isBlank()) {
            throw new RuntimeException("追问内容不能为空，请传入 question/message/userQuery");
        }

        return question.trim();
    }

    private String buildSessionKey(String steamId, String itemId, String itemName) {
        String keyPart;

        if (itemId != null && !itemId.isBlank()) {
            keyPart = "itemId:" + itemId;
        } else {
            keyPart = "itemName:" + itemName;
        }

        return "chat_history:" + steamId + ":" + keyPart;
    }

    private Map<String, String> createMsg(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private List<Map<String, String>> getHistoryFromRedis(String key) {
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<Map<String, String>>>() {}
            );
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private void saveHistoryToRedis(String key, List<Map<String, String>> messages) {
        if (messages.size() > MAX_HISTORY_SIZE) {
            List<Map<String, String>> trimmed = new ArrayList<>();

            trimmed.add(messages.get(0));

            trimmed.addAll(messages.subList(
                    messages.size() - (MAX_HISTORY_SIZE - 1),
                    messages.size()
            ));

            messages = trimmed;
        }

        try {
            String json = objectMapper.writeValueAsString(messages);
            redisTemplate.opsForValue().set(key, json, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean containsAny(String text, String... words) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }

        return false;
    }

    private String blankToNA(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }

        return value;
    }

    private enum FollowUpIntent {
        CURRENT_ITEM,
        COMPARE_ITEM,
        SWITCH_ITEM
    }

    private static class RagResolvedItem {
        private final String itemId;
        private final String itemName;
        private final double confidence;

        private RagResolvedItem(String itemId, String itemName, double confidence) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.confidence = confidence;
        }

        private static RagResolvedItem empty() {
            return new RagResolvedItem(null, null, 0.0);
        }

        private boolean exists() {
            return itemName != null && !itemName.isBlank();
        }
    }
}
package com.example.priceprediction.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class IntentClassifierService {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IntentClassifierService(@Qualifier("intentChatLanguageModel") ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public IntentClassification classify(String userMessage, boolean clientFollowUp) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentClassification.unknown("empty user message");
        }

        try {
            String response = chatLanguageModel.generate(buildPrompt(userMessage, clientFollowUp));
            IntentClassification classification = parse(response);
            log.info("Intent classified, primaryIntent={}, flags={}, itemText={}, action={}, followUp={}, confidence={}, reason={}",
                    classification.primaryIntent(),
                    classification.flags(),
                    classification.itemText(),
                    classification.action(),
                    classification.followUp(),
                    classification.confidence(),
                    classification.reason());
            return classification;
        } catch (Exception e) {
            log.warn("Intent classification failed, fallback to UNKNOWN. message={}", userMessage, e);
            return IntentClassification.unknown("intent classifier failed: " + e.getMessage());
        }
    }

    private String buildPrompt(String userMessage, boolean clientFollowUp) {
        return """
                你是 CS2 饰品分析 Agent 的第一阶段意图分类器。

                任务：
                只判断用户意图并抽取结构化字段，不回答用户问题，不查询数据，不编造饰品。

                可选 primaryIntent：
                - CHAT：闲聊、寒暄、感谢、普通对话，不需要饰品数据。
                - GENERAL_QUESTION：普通问题或系统能力问题，不需要饰品数据。
                - ITEM_QUERY：用户明确提出一个新饰品，想查价格、行情、分析、走势等。
                - ITEM_FOLLOW_UP：用户在追问上一轮饰品，没有明确提出新饰品。
                - ITEM_ACTION：用户围绕某饰品或上一轮饰品询问买、卖、持有、止盈、止损、补仓等动作。
                - ITEM_CORRECTION：用户纠正上一轮识别结果，明确说“不是 A，是 B”“我是问 B”。
                - INVENTORY_QUERY：用户询问自己的库存、持仓、背包。
                - UNKNOWN：无法可靠判断。

                可选 flags，可多选：
                - INVENTORY_LOOKUP：需要查询用户库存、背包、持仓。
                - ITEM_ENTITY_QUERY：需要识别或解析某个饰品实体。
                - PRICE_QUERY：需要查询价格、在售数量、涨跌幅。
                - STRATEGY_ANALYSIS：需要策略/K线/趋势分析。
                - BUY_ADVICE：用户问买入、能不能买、是否值得入。
                - SELL_ADVICE：用户问卖出、要不要出、止盈止损。
                - HOLD_ADVICE：用户问继续拿、持有、观望。
                - FOLLOW_UP：用户追问上一轮上下文。
                - ENTITY_CORRECTION：用户纠正上一轮识别结果。
                - CLARIFICATION：用户在确认候选或补充信息。
                - SMALL_TALK：闲聊。

                字段要求：
                - itemText：只填用户明确提到的饰品名称或别名，例如“子弹皇后”“AK-47 | 红线”。如果只是“这个、刚才那个、1000买的现在卖吗”，填 null。
                - action：buy/sell/hold/analyze/price/trend/inventory/correct/chat/unknown 之一。
                - priceLimit：用户提到的买入价、预算、成本价等数字；没有填 null。
                - wear：崭新出厂/略有磨损/久经沙场/破损不堪/战痕累累/暗金/StatTrak 等；没有填 null。
                - followUp：用户是否在追问上一轮上下文。
                - confidence：0 到 1。
                - reason：一句中文解释。

                重要规则：
                1. “1000买的现在卖掉吗”“那现在还能买吗”“刚才那个要不要出”属于 ITEM_ACTION 或 ITEM_FOLLOW_UP，itemText 必须为 null。
                2. “我是问子弹皇后”“不是皇后，是子弹皇后”属于 ITEM_CORRECTION，itemText=子弹皇后。
                3. “AK-47 | 红线（略有磨损）”属于 ITEM_QUERY，itemText=AK-47 | 红线，wear=略有磨损。
                4. 不要把成本价、动作词、语气词当成 itemText。
                5. 复合问题必须用 flags 表达全部任务，例如“我库存里的龙狙现在要不要卖”：
                   primaryIntent=ITEM_ACTION，
                   flags=[INVENTORY_LOOKUP, ITEM_ENTITY_QUERY, SELL_ADVICE, PRICE_QUERY, STRATEGY_ANALYSIS]，
                   itemText=龙狙。
                6. 只返回 JSON，不要 Markdown，不要解释正文。

                clientFollowUp=%s
                用户 Query：
                %s

                返回 JSON 格式：
                {
                  "primaryIntent": "ITEM_QUERY",
                  "flags": ["ITEM_ENTITY_QUERY", "PRICE_QUERY", "STRATEGY_ANALYSIS"],
                  "itemText": "AK-47 | 红线",
                  "action": "analyze",
                  "priceLimit": null,
                  "wear": "略有磨损",
                  "followUp": false,
                  "confidence": 0.95,
                  "reason": "用户明确给出饰品名和磨损"
                }
                """.formatted(clientFollowUp, userMessage);
    }

    private IntentClassification parse(String response) throws Exception {
        String json = extractJson(response);
        JsonNode root = objectMapper.readTree(json);
        IntentType intentType = parseIntentType(firstNonBlank(text(root, "primaryIntent"), text(root, "intentType")));
        return new IntentClassification(
                intentType,
                parseFlags(root.path("flags")),
                nullableText(root, "itemText"),
                nullableText(root, "action"),
                decimal(root, "priceLimit"),
                nullableText(root, "wear"),
                root.path("followUp").asBoolean(false),
                clamp(root.path("confidence").asDouble(0.0)),
                nullableText(root, "reason")
        );
    }

    private Set<IntentFlag> parseFlags(JsonNode flagsNode) {
        Set<IntentFlag> flags = new LinkedHashSet<>();
        if (flagsNode == null || flagsNode.isMissingNode() || flagsNode.isNull()) {
            return flags;
        }
        if (flagsNode.isArray()) {
            for (JsonNode node : flagsNode) {
                parseFlag(node.asText("")).ifPresent(flags::add);
            }
            return flags;
        }
        for (String part : flagsNode.asText("").split(",")) {
            parseFlag(part).ifPresent(flags::add);
        }
        return flags;
    }

    private Optional<IntentFlag> parseFlag(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(IntentFlag.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response.trim();
    }

    private IntentType parseIntentType(String value) {
        if (value == null || value.isBlank()) {
            return IntentType.UNKNOWN;
        }
        try {
            return IntentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return IntentType.UNKNOWN;
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private String nullableText(JsonNode root, String field) {
        String value = text(root, field);
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || node.asText("").isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AgentPromptBuilder {

    public AgentResponsePlan directChat(String userMessage, IntentClassification intent) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                意图识别结果：
                - intentType：%s
                - confidence：%.2f
                - reason：%s

                系统路由：
                本轮是闲聊或普通问题，不进入 Family Recall、RAG，也不要调用价格/K线/策略/库存工具。
                请直接自然回答用户。
                """.formatted(
                userMessage,
                intent.intentType(),
                intent.confidence(),
                blank(intent.reason())
        ));
    }

    public AgentResponsePlan inventory(String userMessage, IntentClassification intent) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                意图识别结果：
                - intentType：%s
                - confidence：%.2f
                - reason：%s

                系统路由：
                本轮是库存/持仓查询，不进入饰品 RAG。
                如需查询用户库存，只允许调用 getUserInventory；不要把库存问题猜成某个饰品。
                """.formatted(
                userMessage,
                intent.intentType(),
                intent.confidence(),
                blank(intent.reason())
        ), Set.of(AgentResponsePlan.TOOL_GET_USER_INVENTORY));
    }

    public AgentResponsePlan contextualFollowUp(
            String userMessage,
            AgentTaskState state,
            String cnName,
            String marketHashName,
            IntentClassification intent
    ) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                多轮追问上下文：
                - 本轮用户没有明确提出新的饰品名，必须沿用上一轮已确认的饰品。
                - 用户提到的买入价：%s
                - 当前 item_id：%s
                - 当前中文名：%s
                - 当前 Steam market_hash_name：%s
                - 上一次工具：%s
                - 上一次工具状态：%s

                重要约束：
                1. 这是针对上一轮饰品的追问，不要重新 RAG 识别成其它饰品。
                2. 如果需要调用价格 / K 线 / 策略工具，必须使用 item_id：%s。
                3. 如果用户明确要求换另一个饰品，才允许切换实体。
                4. 如果用户提供了买入价，回答卖不卖时必须结合买入价计算浮盈/浮亏。
                5. 回答用户时，优先围绕当前饰品给出建议，并区分工具事实、策略信号和判断。
                """.formatted(
                userMessage,
                intent.priceLimit() == null ? "未提供" : intent.priceLimit(),
                state.getCurrentItemId(),
                cnName,
                blank(marketHashName),
                blank(state.getLastToolName()),
                blank(state.getLastToolStatus()),
                state.getCurrentItemId()
        ), itemContextTools(intent));
    }

    public AgentResponsePlan missingTaskState(String userMessage, IntentClassification intent) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                意图识别结果：
                - intentType：%s
                - action：%s
                - priceLimit：%s
                - followUp：%s
                - confidence：%.2f
                - reason：%s

                系统路由：
                本轮是围绕上一轮饰品的追问/买卖动作，但当前没有已确认的 Task State。
                不要进入 Family Recall 或 RAG。
                请让用户补充具体饰品名称或重新说一次要分析的饰品。
                """.formatted(
                userMessage,
                intent.intentType(),
                blank(intent.action()),
                intent.priceLimit() == null ? "" : intent.priceLimit(),
                intent.followUp(),
                intent.confidence(),
                blank(intent.reason())
        ));
    }

    public AgentResponsePlan unknown(String userMessage, IntentClassification intent) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                意图识别结果：
                - intentType：UNKNOWN
                - confidence：%.2f
                - reason：%s

                系统路由：
                本轮意图不确定，保守处理，不进入饰品 RAG，不调用价格/K线/策略工具。
                请自然追问用户想查询什么，或让用户补充具体饰品名称。
                """.formatted(
                userMessage,
                intent.confidence(),
                blank(intent.reason())
        ));
    }

    public AgentResponsePlan noFamily(String userMessage) {
        return AgentResponsePlan.askConfirmation("""
                我没有从饰品 Family 库中可靠识别到你要分析的饰品，先不查询价格或 K 线，避免拿错数据。

                请你补充更具体的饰品名称，最好带上武器和磨损，例如“AK-47 | 红线（略有磨损）”。
                """);
    }

    public AgentResponsePlan familyNoExecutableItem(String userMessage, ItemFamilyRecallCandidate family) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                Family 识别结果：
                - family_key：%s
                - Family 名称：%s
                - 置信度：%.2f

                系统已经识别到饰品大类，但没有在 cs_qaq_item_id 表中找到可执行的具体 item_id。
                不允许编造 item_id、磨损版本、价格、成交量或 K 线数据。
                如果需要继续查询，请让用户补充完整饰品名称或磨损。
                """.formatted(userMessage, family.getFamilyKey(), family.getName(), family.getFinalScore()));
    }

    public AgentResponsePlan familySelected(
            String userMessage,
            ItemFamilyRecallCandidate family,
            ItemFamilyRecallResult recall,
            CsQaqItemIdEntity item
    ) {
        return AgentResponsePlan.continueWith("""
                用户原始问题：
                %s

                Family 识别结果：
                - family_key：%s
                - Family 名称：%s
                - 置信度：%.2f
                - TopK gap：%.2f
                - 召回来源：%s
                - 重排依据：%s

                系统已通过 cs_qaq_item_id 表选择最终可执行饰品：
                - 最终 item_id：%d
                - 最终中文名：%s
                - 最终 Steam market_hash_name：%s

                磨损选择规则：
                1. 如果用户 Query 明确指定磨损或暗金版本，以用户指定为准。
                2. 如果用户 Query 没有指定磨损，默认优先选择崭新出厂；如果该 Family 没有崭新，则依次选择略有磨损、久经沙场、破损不堪、战痕累累。
                3. 如果用户 Query 没有指定 StatTrak/暗金，默认选择普通版本。

                重要约束：
                1. 用户当前追问的饰品，必须以上面的“最终可执行饰品”为准。
                2. 如果后续调用 QAQ / 价格 / K 线接口，必须优先使用 item_id：%d。
                3. 回答用户时，优先使用最终中文名：%s。
                4. 不允许编造其它饰品、其它磨损、其它 item_id、价格、成交量或 K 线数据。
                """.formatted(
                userMessage,
                family.getFamilyKey(),
                family.getName(),
                recall.getConfidence(),
                recall.getTopGap(),
                String.join("+", family.getSources()),
                family.getReason(),
                item.getItemId(),
                item.getCnName(),
                item.getMarketHashName(),
                item.getItemId(),
                item.getCnName()
        ));
    }

    private String blank(String value) {
        return value == null ? "" : value;
    }

    public Set<String> itemContextTools(IntentClassification intent) {
        Set<String> tools = new LinkedHashSet<>();
        if (intent != null && intent.hasFlag(IntentFlag.INVENTORY_LOOKUP)) {
            tools.add(AgentResponsePlan.TOOL_GET_USER_INVENTORY);
        }
        tools.add(AgentResponsePlan.TOOL_GET_ITEM_PRICE_DATA);
        tools.add(AgentResponsePlan.TOOL_RUN_ITEM_STRATEGY_ANALYSIS);
        return tools;
    }
}

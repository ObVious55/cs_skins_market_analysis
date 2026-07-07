package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IntentRouter {

    private final AgentTaskStateService agentTaskStateService;
    private final CsQaqItemIdLookupService csQaqItemIdLookupService;
    private final AgentPromptBuilder promptBuilder;

    public IntentRouter(
            AgentTaskStateService agentTaskStateService,
            CsQaqItemIdLookupService csQaqItemIdLookupService,
            AgentPromptBuilder promptBuilder
    ) {
        this.agentTaskStateService = agentTaskStateService;
        this.csQaqItemIdLookupService = csQaqItemIdLookupService;
        this.promptBuilder = promptBuilder;
    }

    public Optional<AgentResponsePlan> route(String memoryId, String userMessage, IntentClassification intent) {
        IntentType intentType = intent.intentType() == null ? IntentType.UNKNOWN : intent.intentType();
        String itemText = normalizeItemText(intent.itemText());

        if (intentType == IntentType.CHAT || intentType == IntentType.GENERAL_QUESTION) {
            return Optional.of(promptBuilder.directChat(userMessage, intent));
        }

        if (intentType == IntentType.INVENTORY_QUERY
                && itemText.isBlank()
                && !intent.hasFlag(IntentFlag.ITEM_ENTITY_QUERY)
                && !intent.hasFlag(IntentFlag.BUY_ADVICE)
                && !intent.hasFlag(IntentFlag.SELL_ADVICE)
                && !intent.hasFlag(IntentFlag.HOLD_ADVICE)
                && !intent.hasFlag(IntentFlag.PRICE_QUERY)
                && !intent.hasFlag(IntentFlag.STRATEGY_ANALYSIS)) {
            return Optional.of(promptBuilder.inventory(userMessage, intent));
        }

        boolean contextualIntent = intentType == IntentType.ITEM_FOLLOW_UP
                || (intentType == IntentType.ITEM_ACTION && itemText.isBlank())
                || (intent.followUp() && itemText.isBlank());
        if (contextualIntent) {
            Optional<AgentTaskState> stateOpt = agentTaskStateService.find(memoryId);
            if (stateOpt.isEmpty()) {
                return Optional.of(promptBuilder.missingTaskState(userMessage, intent));
            }

            AgentTaskState state = stateOpt.get();
            Optional<CsQaqItemIdEntity> itemOpt = csQaqItemIdLookupService.findByItemId(state.getCurrentItemId());
            String cnName = itemOpt.map(CsQaqItemIdEntity::getCnName).orElse("");
            String marketHashName = itemOpt
                    .map(CsQaqItemIdEntity::getMarketHashName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(state.getCurrentMarketHashName());
            return Optional.of(promptBuilder.contextualFollowUp(
                    userMessage,
                    state,
                    cnName,
                    marketHashName,
                    intent
            ));
        }

        if (intentType == IntentType.UNKNOWN && itemText.isBlank()) {
            return Optional.of(promptBuilder.unknown(userMessage, intent));
        }

        return Optional.empty();
    }

    private String normalizeItemText(String itemText) {
        if (itemText == null || itemText.isBlank() || "null".equalsIgnoreCase(itemText.trim())) {
            return "";
        }
        return itemText.trim();
    }
}

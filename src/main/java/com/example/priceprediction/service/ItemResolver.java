package com.example.priceprediction.service;

import com.example.priceprediction.entity.CsQaqItemIdEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ItemResolver {

    private final HybridFamilyRecallService hybridFamilyRecallService;
    private final ItemFamilyVariantSelector itemFamilyVariantSelector;
    private final AgentTaskStateService agentTaskStateService;
    private final AgentPromptBuilder promptBuilder;
    private final ClarificationService clarificationService;

    public ItemResolver(
            HybridFamilyRecallService hybridFamilyRecallService,
            ItemFamilyVariantSelector itemFamilyVariantSelector,
            AgentTaskStateService agentTaskStateService,
            AgentPromptBuilder promptBuilder,
            ClarificationService clarificationService
    ) {
        this.hybridFamilyRecallService = hybridFamilyRecallService;
        this.itemFamilyVariantSelector = itemFamilyVariantSelector;
        this.agentTaskStateService = agentTaskStateService;
        this.promptBuilder = promptBuilder;
        this.clarificationService = clarificationService;
    }

    public AgentResponsePlan resolve(String memoryId, String userMessage, IntentClassification intent) {
        String recallQuery = normalizeItemText(intent.itemText());
        if (recallQuery.isBlank()) {
            return promptBuilder.unknown(userMessage, intent);
        }

        ItemFamilyRecallResult familyRecall = hybridFamilyRecallService.recall(recallQuery, 10);
        if (familyRecall != null) {
            AgentResponsePlan familyPlan = resolveFamily(memoryId, userMessage, intent, familyRecall);
            if (familyPlan != null) {
                return familyPlan;
            }
        }

        return promptBuilder.noFamily(userMessage);
    }

    private AgentResponsePlan resolveFamily(
            String memoryId,
            String userMessage,
            IntentClassification intent,
            ItemFamilyRecallResult familyRecall
    ) {
        ItemFamilyRecallCandidate primaryFamily = familyRecall.getPrimary();
        if (primaryFamily == null || primaryFamily.getFamilyKey() == null || primaryFamily.getFamilyKey().isBlank()) {
            return promptBuilder.noFamily(userMessage);
        }

        log.info(
                "Family recall result, familyKey={}, name={}, confidence={}, topGap={}, reliable={}, sources={}",
                primaryFamily.getFamilyKey(),
                primaryFamily.getName(),
                familyRecall.getConfidence(),
                familyRecall.getTopGap(),
                familyRecall.isReliable(),
                primaryFamily.getSources()
        );

        if (!familyRecall.isReliable()) {
            return clarificationService.lowConfidenceFamily(familyRecall);
        }

        Optional<CsQaqItemIdEntity> selectedItemOpt =
                itemFamilyVariantSelector.selectVariant(primaryFamily, userMessage);
        if (selectedItemOpt.isEmpty()) {
            return promptBuilder.familyNoExecutableItem(userMessage, primaryFamily);
        }

        CsQaqItemIdEntity selectedItem = selectedItemOpt.get();
        log.info(
                "Family variant selected, familyKey={}, itemId={}, cnName={}, marketHashName={}",
                primaryFamily.getFamilyKey(),
                selectedItem.getItemId(),
                selectedItem.getCnName(),
                selectedItem.getMarketHashName()
        );

        agentTaskStateService.confirmItem(
                memoryId,
                selectedItem.getItemId().toString(),
                selectedItem.getCnName(),
                selectedItem.getMarketHashName(),
                "FAMILY_RECALL"
        );

        return promptBuilder.familySelected(userMessage, primaryFamily, familyRecall, selectedItem)
                .withAllowedTools(promptBuilder.itemContextTools(intent));
    }

    private String normalizeItemText(String itemText) {
        if (itemText == null || itemText.isBlank() || "null".equalsIgnoreCase(itemText.trim())) {
            return "";
        }
        return itemText.trim();
    }
}

package com.example.priceprediction.service;

import java.util.List;

public class ItemFamilyDocument {

    private final String familyKey;
    private final String titleCn;
    private final String titleEn;
    private final String weapon;
    private final String skinCn;
    private final String skinEn;
    private final Long defaultItemId;
    private final List<Long> variantItemIds;
    private final List<String> searchTerms;
    private final List<String> tokens;

    public ItemFamilyDocument(
            String familyKey,
            String titleCn,
            String titleEn,
            String weapon,
            String skinCn,
            String skinEn,
            Long defaultItemId,
            List<Long> variantItemIds,
            List<String> searchTerms,
            List<String> tokens
    ) {
        this.familyKey = familyKey;
        this.titleCn = titleCn;
        this.titleEn = titleEn;
        this.weapon = weapon;
        this.skinCn = skinCn;
        this.skinEn = skinEn;
        this.defaultItemId = defaultItemId;
        this.variantItemIds = variantItemIds;
        this.searchTerms = searchTerms;
        this.tokens = tokens;
    }

    public String getFamilyKey() {
        return familyKey;
    }

    public String getTitleCn() {
        return titleCn;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getWeapon() {
        return weapon;
    }

    public String getSkinCn() {
        return skinCn;
    }

    public String getSkinEn() {
        return skinEn;
    }

    public Long getDefaultItemId() {
        return defaultItemId;
    }

    public List<Long> getVariantItemIds() {
        return variantItemIds;
    }

    public List<String> getSearchTerms() {
        return searchTerms;
    }

    public List<String> getTokens() {
        return tokens;
    }
}

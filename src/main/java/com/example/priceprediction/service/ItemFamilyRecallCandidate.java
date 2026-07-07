package com.example.priceprediction.service;

import java.util.ArrayList;
import java.util.List;

public class ItemFamilyRecallCandidate {

    private String familyKey;
    private String name;
    private String source;
    private Long defaultItemId;
    private List<Long> variantItemIds = new ArrayList<>();
    private double lexicalScore;
    private double vectorScore;
    private double finalScore;
    private List<String> sources = new ArrayList<>();
    private List<String> matchedTerms = new ArrayList<>();
    private String reason;

    public String getFamilyKey() {
        return familyKey;
    }

    public void setFamilyKey(String familyKey) {
        this.familyKey = familyKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getDefaultItemId() {
        return defaultItemId;
    }

    public void setDefaultItemId(Long defaultItemId) {
        this.defaultItemId = defaultItemId;
    }

    public List<Long> getVariantItemIds() {
        return variantItemIds;
    }

    public void setVariantItemIds(List<Long> variantItemIds) {
        this.variantItemIds = variantItemIds == null ? new ArrayList<>() : variantItemIds;
    }

    public double getLexicalScore() {
        return lexicalScore;
    }

    public void setLexicalScore(double lexicalScore) {
        this.lexicalScore = lexicalScore;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    public void setMatchedTerms(List<String> matchedTerms) {
        this.matchedTerms = matchedTerms == null ? new ArrayList<>() : matchedTerms;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

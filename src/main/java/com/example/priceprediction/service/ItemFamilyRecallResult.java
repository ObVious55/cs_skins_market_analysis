package com.example.priceprediction.service;

import java.util.List;

public class ItemFamilyRecallResult {

    private final String query;
    private final ItemFamilyRecallCandidate primary;
    private final double confidence;
    private final double topGap;
    private final boolean reliable;
    private final List<ItemFamilyRecallCandidate> candidates;

    public ItemFamilyRecallResult(
            String query,
            ItemFamilyRecallCandidate primary,
            double confidence,
            double topGap,
            boolean reliable,
            List<ItemFamilyRecallCandidate> candidates
    ) {
        this.query = query;
        this.primary = primary;
        this.confidence = confidence;
        this.topGap = topGap;
        this.reliable = reliable;
        this.candidates = candidates;
    }

    public String getQuery() {
        return query;
    }

    public ItemFamilyRecallCandidate getPrimary() {
        return primary;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getTopGap() {
        return topGap;
    }

    public boolean isReliable() {
        return reliable;
    }

    public List<ItemFamilyRecallCandidate> getCandidates() {
        return candidates;
    }
}

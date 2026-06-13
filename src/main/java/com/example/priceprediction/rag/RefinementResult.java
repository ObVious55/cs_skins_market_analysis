package com.example.priceprediction.rag;

import lombok.Getter;

import java.util.List;

@Getter
public class RefinementResult {
    private final String primaryItemId;
    private final String primaryName;
    private final double confidence;
    private final List<RefinedCandidate> candidates;

    public RefinementResult(String primaryItemId, String primaryName, double confidence, List<RefinedCandidate> candidates) {
        this.primaryItemId = primaryItemId;
        this.primaryName = primaryName;
        this.confidence = confidence;
        this.candidates = candidates;
    }

}


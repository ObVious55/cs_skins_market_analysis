package com.example.priceprediction.rag;

import lombok.Getter;

import java.util.Map;

@Getter
public class RefinedCandidate {
    private final String itemId;
    private final String name;
    private final double score;
    private final double vectorScore;
    private final double lexicalScore;
    private final Map<String, Object> metadata;

    public RefinedCandidate(String itemId, String name, double score, double vectorScore, double lexicalScore, Map<String, Object> metadata) {
        this.itemId = itemId;
        this.name = name;
        this.score = score;
        this.vectorScore = vectorScore;
        this.lexicalScore = lexicalScore;
        this.metadata = metadata;
    }

}


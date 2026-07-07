package com.example.priceprediction.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class Bm25FamilyRecallService {

    private final ItemFamilyIndexService itemFamilyIndexService;

    public Bm25FamilyRecallService(ItemFamilyIndexService itemFamilyIndexService) {
        this.itemFamilyIndexService = itemFamilyIndexService;
    }

    public List<ItemFamilyRecallCandidate> recall(String query, int topK) {
        List<String> queryTokens = itemFamilyIndexService.tokenize(removeNoiseWords(query));
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ItemFamilyRecallCandidate> candidates = new ArrayList<>();
        for (ItemFamilyDocument document : itemFamilyIndexService.getFamilyDocuments()) {
            ScoredMatch scoredMatch = score(document, queryTokens);
            if (scoredMatch.score() <= 0) {
                continue;
            }

            ItemFamilyRecallCandidate candidate = new ItemFamilyRecallCandidate();
            candidate.setFamilyKey(document.getFamilyKey());
            candidate.setName(document.getTitleCn());
            candidate.setDefaultItemId(document.getDefaultItemId());
            candidate.setVariantItemIds(document.getVariantItemIds());
            candidate.setSource("BM25");
            candidate.setLexicalScore(scoredMatch.score());
            candidate.setFinalScore(scoredMatch.score());
            candidate.setSources(List.of("BM25"));
            candidate.setMatchedTerms(scoredMatch.matchedTerms());
            candidate.setReason("BM25 lexical match: " + String.join(", ", scoredMatch.matchedTerms()));
            candidates.add(candidate);
        }

        double maxScore = candidates.stream()
                .mapToDouble(ItemFamilyRecallCandidate::getLexicalScore)
                .max()
                .orElse(0.0);

        if (maxScore > 0) {
            for (ItemFamilyRecallCandidate candidate : candidates) {
                candidate.setLexicalScore(candidate.getLexicalScore() / maxScore);
                candidate.setFinalScore(candidate.getLexicalScore());
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(ItemFamilyRecallCandidate::getLexicalScore).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private ScoredMatch score(ItemFamilyDocument document, List<String> queryTokens) {
        Set<String> matchedTerms = new LinkedHashSet<>();
        double score = 0.0;

        String searchableText = itemFamilyIndexService.normalize(String.join(" ", document.getSearchTerms()));
        Set<String> documentTokens = new LinkedHashSet<>(document.getTokens());

        for (String token : queryTokens) {
            if (token.length() <= 1) {
                continue;
            }

            if (documentTokens.contains(token)) {
                matchedTerms.add(token);
                score += 3.0;
                continue;
            }

            if (searchableText.contains(token)) {
                matchedTerms.add(token);
                score += token.length() >= 2 ? 2.0 : 0.5;
                continue;
            }

            if (isCjkSubsequenceMatch(token, searchableText)) {
                matchedTerms.add(token);
                score += 1.5;
            }
        }

        String normalizedQuery = itemFamilyIndexService.normalize(String.join("", queryTokens));
        if (!normalizedQuery.isBlank() && searchableText.contains(normalizedQuery)) {
            matchedTerms.add(normalizedQuery);
            score += 4.0;
        }

        if (containsExact(document.getSkinCn(), queryTokens)) {
            score += 5.0;
            matchedTerms.add(document.getSkinCn());
        }
        if (containsExact(document.getTitleCn(), queryTokens)) {
            score += 3.0;
            matchedTerms.add(document.getTitleCn());
        }

        return new ScoredMatch(score, new ArrayList<>(matchedTerms));
    }

    private boolean containsExact(String value, List<String> queryTokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = itemFamilyIndexService.normalize(value);
        return queryTokens.stream().anyMatch(token -> token.equals(normalizedValue) || normalizedValue.contains(token));
    }

    private boolean isCjkSubsequenceMatch(String token, String searchableText) {
        if (token.length() < 2 || !containsHan(token) || searchableText == null || searchableText.isBlank()) {
            return false;
        }
        return isSubsequence(token, searchableText);
    }

    private boolean containsHan(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private boolean isSubsequence(String needle, String haystack) {
        int needleIndex = 0;
        int needleLength = needle.length();
        for (int haystackIndex = 0; haystackIndex < haystack.length() && needleIndex < needleLength; haystackIndex++) {
            if (needle.charAt(needleIndex) == haystack.charAt(haystackIndex)) {
                needleIndex++;
            }
        }
        return needleIndex == needleLength;
    }

    private String removeNoiseWords(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase(Locale.ROOT)
                .replaceAll("(现在|最近|目前|价格|多少钱|多少|能买吗|能不能买|值得买吗|还会涨吗|还会跌吗|可不可以|继续|值不值得|怎么样|建议|分析|行情|涨价|会涨|会跌|买不买|入手|可以入|看看|帮我|捏|我)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ScoredMatch(double score, List<String> matchedTerms) {
    }
}

package com.example.priceprediction.service;

import com.example.priceprediction.rag.ItemRagRetriever;
import com.example.priceprediction.rag.VectorStoreClient;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridFamilyRecallService {

    private static final double RELIABLE_SCORE_THRESHOLD = 0.55;
    private static final double RELIABLE_GAP_THRESHOLD = 0;

    private final Bm25FamilyRecallService bm25FamilyRecallService;
    private final ItemRagRetriever itemRagRetriever;

    public HybridFamilyRecallService(
            Bm25FamilyRecallService bm25FamilyRecallService,
            ItemRagRetriever itemRagRetriever
    ) {
        this.bm25FamilyRecallService = bm25FamilyRecallService;
        this.itemRagRetriever = itemRagRetriever;
    }

    public ItemFamilyRecallResult recall(String query, int topK) {
        int limit = Math.max(1, topK);
        List<ItemFamilyRecallCandidate> lexicalCandidates = bm25FamilyRecallService.recall(query, limit);
        List<ItemFamilyRecallCandidate> vectorCandidates = vectorRecall(query, limit);

        Map<String, ItemFamilyRecallCandidate> merged = new LinkedHashMap<>();
        mergeCandidates(merged, lexicalCandidates);
        mergeCandidates(merged, vectorCandidates);

        List<ItemFamilyRecallCandidate> sorted = merged.values().stream()
                .peek(this::scoreMergedCandidate)
                .sorted(Comparator.comparingDouble(ItemFamilyRecallCandidate::getFinalScore).reversed())
                .limit(limit)
                .toList();

        ItemFamilyRecallCandidate primary = sorted.isEmpty() ? null : sorted.get(0);
        double confidence = primary == null ? 0.0 : Math.min(1.0, primary.getFinalScore());
        double secondScore = sorted.size() > 1 ? sorted.get(1).getFinalScore() : 0.0;
        double topGap = primary == null ? 0.0 : Math.max(0.0, primary.getFinalScore() - secondScore);
        boolean reliable = primary != null
                && confidence >= RELIABLE_SCORE_THRESHOLD
                && (topGap >= RELIABLE_GAP_THRESHOLD || primary.getSources().size() >= 2);

        return new ItemFamilyRecallResult(query, primary, confidence, topGap, reliable, sorted);
    }

    private List<ItemFamilyRecallCandidate> vectorRecall(String query, int topK) {
        List<VectorStoreClient.VectorSearchResult> results = itemRagRetriever.retrieveFamily(query, topK);
        if (results.isEmpty()) {
            return List.of();
        }

        double maxVectorScore = results.stream()
                .mapToDouble(VectorStoreClient.VectorSearchResult::getScore)
                .max()
                .orElse(0.0);

        List<ItemFamilyRecallCandidate> candidates = new ArrayList<>();
        for (VectorStoreClient.VectorSearchResult result : results) {
            Map<String, Object> metadata = result.getMetadata();
            if (metadata == null) {
                continue;
            }

            String familyKey = stringValue(metadata.get("family_key"));
            if (familyKey.isBlank()) {
                continue;
            }

            ItemFamilyRecallCandidate candidate = new ItemFamilyRecallCandidate();
            candidate.setFamilyKey(familyKey);
            candidate.setName(firstNonBlank(
                    stringValue(metadata.get("name")),
                    stringValue(metadata.get("cn_name")),
                    stringValue(metadata.get("title"))
            ));
            candidate.setDefaultItemId(longValue(metadata.get("default_item_id")));
            candidate.setVariantItemIds(longListValue(metadata.get("variant_item_ids")));
            candidate.setSource("VECTOR_FAMILY");
            double vectorNorm = maxVectorScore <= 0 ? 0.0 : result.getScore() / maxVectorScore;
            candidate.setVectorScore(vectorNorm);
            candidate.setFinalScore(vectorNorm);
            candidate.setSources(List.of("VECTOR_FAMILY"));
            candidate.setReason("Family vector recall score=" + String.format("%.4f", result.getScore()));
            candidates.add(candidate);
        }
        return candidates;
    }

    private void mergeCandidates(
            Map<String, ItemFamilyRecallCandidate> merged,
            List<ItemFamilyRecallCandidate> candidates
    ) {
        for (ItemFamilyRecallCandidate candidate : candidates) {
            if (candidate.getFamilyKey() == null || candidate.getFamilyKey().isBlank()) {
                continue;
            }

            ItemFamilyRecallCandidate target = merged.computeIfAbsent(candidate.getFamilyKey(), ignored -> {
                ItemFamilyRecallCandidate created = new ItemFamilyRecallCandidate();
                created.setFamilyKey(candidate.getFamilyKey());
                created.setName(candidate.getName());
                created.setDefaultItemId(candidate.getDefaultItemId());
                created.setVariantItemIds(candidate.getVariantItemIds());
                return created;
            });

            if (target.getName() == null || target.getName().isBlank()) {
                target.setName(candidate.getName());
            }
            if (target.getDefaultItemId() == null) {
                target.setDefaultItemId(candidate.getDefaultItemId());
            }
            target.setVariantItemIds(mergeLongs(target.getVariantItemIds(), candidate.getVariantItemIds()));

            target.setLexicalScore(Math.max(target.getLexicalScore(), candidate.getLexicalScore()));
            target.setVectorScore(Math.max(target.getVectorScore(), candidate.getVectorScore()));

            List<String> sources = new ArrayList<>(target.getSources());
            for (String source : candidate.getSources()) {
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }
            target.setSources(sources);

            List<String> matchedTerms = new ArrayList<>(target.getMatchedTerms());
            for (String term : candidate.getMatchedTerms()) {
                if (!matchedTerms.contains(term)) {
                    matchedTerms.add(term);
                }
            }
            target.setMatchedTerms(matchedTerms);
        }
    }

    private void scoreMergedCandidate(ItemFamilyRecallCandidate candidate) {
        double agreementBonus = candidate.getSources().size() >= 2 ? 0.12 : 0.0;
        double lexicalWeight = isShortLexicalCandidate(candidate) ? 0.60 : 0.35;
        double vectorWeight = isShortLexicalCandidate(candidate) ? 0.25 : 0.50;

        double finalScore = lexicalWeight * candidate.getLexicalScore()
                + vectorWeight * candidate.getVectorScore()
                + agreementBonus;

        if (candidate.getLexicalScore() == 0.0 && candidate.getVectorScore() >= 0.95) {
            finalScore = Math.max(finalScore, 0.50);
        }

        candidate.setFinalScore(Math.min(1.0, finalScore));
        candidate.setSource(String.join("+", candidate.getSources()));
        candidate.setReason(
                "lexical=" + String.format("%.2f", candidate.getLexicalScore())
                        + ", vector=" + String.format("%.2f", candidate.getVectorScore())
                        + ", sources=" + candidate.getSources()
        );
    }

    private boolean isShortLexicalCandidate(ItemFamilyRecallCandidate candidate) {
        return candidate.getLexicalScore() >= 0.9 && candidate.getVectorScore() < 0.70;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> longListValue(Object value) {
        if (value instanceof List<?> values) {
            List<Long> ids = new ArrayList<>();
            for (Object item : values) {
                Long parsed = longValue(item);
                if (parsed != null && !ids.contains(parsed)) {
                    ids.add(parsed);
                }
            }
            return ids;
        }
        if (value instanceof String text && text.contains(",")) {
            List<Long> ids = new ArrayList<>();
            for (String part : text.replace("[", "").replace("]", "").split(",")) {
                Long parsed = longValue(part.trim());
                if (parsed != null && !ids.contains(parsed)) {
                    ids.add(parsed);
                }
            }
            return ids;
        }
        Long singleValue = longValue(value);
        return singleValue == null ? List.of() : List.of(singleValue);
    }

    private List<Long> mergeLongs(List<Long> first, List<Long> second) {
        List<Long> merged = new ArrayList<>();
        if (first != null) {
            for (Long value : first) {
                if (value != null && !merged.contains(value)) {
                    merged.add(value);
                }
            }
        }
        if (second != null) {
            for (Long value : second) {
                if (value != null && !merged.contains(value)) {
                    merged.add(value);
                }
            }
        }
        return merged;
    }

}

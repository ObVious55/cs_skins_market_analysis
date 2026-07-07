package com.example.priceprediction.controller;

import com.example.priceprediction.rag.ItemRagRetriever;
import com.example.priceprediction.rag.VectorStoreClient;
import com.example.priceprediction.service.Bm25FamilyRecallService;
import com.example.priceprediction.service.HybridFamilyRecallService;
import com.example.priceprediction.service.ItemFamilyRecallCandidate;
import com.example.priceprediction.service.ItemFamilyRecallResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/family-recall")
@CrossOrigin(origins = "*")
public class DebugFamilyRecallController {

    private final Bm25FamilyRecallService bm25FamilyRecallService;
    private final ItemRagRetriever itemRagRetriever;
    private final HybridFamilyRecallService hybridFamilyRecallService;

    public DebugFamilyRecallController(
            Bm25FamilyRecallService bm25FamilyRecallService,
            ItemRagRetriever itemRagRetriever,
            HybridFamilyRecallService hybridFamilyRecallService
    ) {
        this.bm25FamilyRecallService = bm25FamilyRecallService;
        this.itemRagRetriever = itemRagRetriever;
        this.hybridFamilyRecallService = hybridFamilyRecallService;
    }

    @GetMapping("/bm25")
    public Map<String, Object> bm25(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK
    ) {
        int limit = normalizeTopK(topK);
        List<ItemFamilyRecallCandidate> candidates = bm25FamilyRecallService.recall(query, limit);
        return Map.of(
                "query", query,
                "topK", limit,
                "count", candidates.size(),
                "candidates", candidates.stream().map(this::candidateView).toList()
        );
    }

    @GetMapping("/vector")
    public Map<String, Object> vector(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK
    ) {
        int limit = normalizeTopK(topK);
        List<VectorStoreClient.VectorSearchResult> results = itemRagRetriever.retrieveFamily(query, limit);
        return Map.of(
                "query", query,
                "topK", limit,
                "count", results.size(),
                "candidates", results.stream().map(this::vectorView).toList()
        );
    }

    @GetMapping("/hybrid")
    public Map<String, Object> hybrid(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK
    ) {
        int limit = normalizeTopK(topK);
        ItemFamilyRecallResult result = hybridFamilyRecallService.recall(query, limit);
        return Map.of(
                "query", query,
                "topK", limit,
                "confidence", result.getConfidence(),
                "topGap", result.getTopGap(),
                "reliable", result.isReliable(),
                "primary", result.getPrimary() == null ? Map.of() : candidateView(result.getPrimary()),
                "candidates", result.getCandidates().stream().map(this::candidateView).toList()
        );
    }

    private int normalizeTopK(int topK) {
        return Math.max(1, Math.min(topK, 50));
    }

    private Map<String, Object> candidateView(ItemFamilyRecallCandidate candidate) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("familyKey", value(candidate.getFamilyKey()));
        view.put("name", value(candidate.getName()));
        view.put("source", value(candidate.getSource()));
        view.put("sources", candidate.getSources());
        view.put("defaultItemId", candidate.getDefaultItemId() == null ? "" : candidate.getDefaultItemId());
        view.put("variantItemIds", candidate.getVariantItemIds());
        view.put("lexicalScore", candidate.getLexicalScore());
        view.put("vectorScore", candidate.getVectorScore());
        view.put("finalScore", candidate.getFinalScore());
        view.put("matchedTerms", candidate.getMatchedTerms());
        view.put("reason", value(candidate.getReason()));
        return view;
    }

    private Map<String, Object> vectorView(VectorStoreClient.VectorSearchResult result) {
        return Map.of(
                "id", value(result.getId()),
                "score", result.getScore(),
                "content", value(result.getContent()),
                "metadata", result.getMetadata() == null ? Map.of() : result.getMetadata()
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}

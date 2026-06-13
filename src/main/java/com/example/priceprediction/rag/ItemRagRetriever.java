package com.example.priceprediction.rag;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemRagRetriever {

    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public ItemRagRetriever(EmbeddingClient embeddingClient,
                            VectorStoreClient vectorStoreClient) {
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    public List<VectorStoreClient.VectorSearchResult> retrieve(String userQuery, int topK) {
        if (userQuery == null || userQuery.isBlank()) {
            return List.of();
        }

        List<Float> queryVector = embeddingClient.embed(userQuery);

        return vectorStoreClient.search(queryVector, topK);
    }

    // 新增：检索并对 topK 候选做名称/候选优化，返回 refinement 结果
    public RefinementResult retrieveAndOptimize(String userQuery, int topK) {
        List<VectorStoreClient.VectorSearchResult> results = retrieve(userQuery, topK);
        System.out.println("========== RAG Query ==========");
        TopKQueryOptimizer optimizer = new TopKQueryOptimizer();
        return optimizer.refine(results, userQuery, Math.min(topK, 10));
    }
}
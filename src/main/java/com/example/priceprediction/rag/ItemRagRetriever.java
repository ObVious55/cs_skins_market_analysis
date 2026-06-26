package com.example.priceprediction.rag;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemRagRetriever {

    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStoreClient vectorStoreClient;

    public ItemRagRetriever(EmbeddingClient embeddingClient,
                            QdrantVectorStoreClient vectorStoreClient) {
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    public List<VectorStoreClient.VectorSearchResult> retrieveFamily(String userQuery, int topK) {
        if (userQuery == null || userQuery.isBlank()) {
            return List.of();
        }

        List<Float> queryVector = embeddingClient.embed(userQuery);
        return vectorStoreClient.searchFamily(queryVector, topK);
    }
}

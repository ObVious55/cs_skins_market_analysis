package com.example.priceprediction.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItemRagIndexService {

    private static final int BATCH_SIZE = 100;

    private final ItemRagDocumentBuilder documentBuilder;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStoreClient vectorStoreClient;

    public ItemRagIndexService(ItemRagDocumentBuilder documentBuilder,
                               EmbeddingClient embeddingClient,
                               QdrantVectorStoreClient vectorStoreClient) {
        this.documentBuilder = documentBuilder;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    public void rebuildIndex() {
        rebuildFamilyIndex();
    }

    public void rebuildFamilyIndex() {
        rebuildDocuments("family", documentBuilder.buildFamilyDocuments());
    }

    private void rebuildDocuments(String indexName, List<ItemRagDocument> documents) {
        System.out.println("Prepare to write " + indexName + " RAG documents: " + documents.size());

        List<VectorStoreClient.VectorRecord> batch = new ArrayList<>();
        int total = documents.size();
        int scannedCount = 0;
        int skippedCount = 0;
        int insertedCount = 0;

        for (ItemRagDocument doc : documents) {
            scannedCount++;

            if (vectorStoreClient.familyExists(doc.getDocId())) {
                skippedCount++;
                if (scannedCount % 100 == 0) {
                    logProgress(indexName, scannedCount, total, skippedCount, insertedCount);
                }
                continue;
            }

            List<Float> vector = embeddingClient.embed(doc.getContent());
            batch.add(new VectorStoreClient.VectorRecord(
                    doc.getDocId(),
                    vector,
                    doc.getContent(),
                    doc.getMetadata()
            ));
            insertedCount++;

            if (batch.size() >= BATCH_SIZE) {
                vectorStoreClient.upsertFamilyBatch(batch);
                batch.clear();
                logProgress(indexName, scannedCount, total, skippedCount, insertedCount);
            }
        }

        if (!batch.isEmpty()) {
            vectorStoreClient.upsertFamilyBatch(batch);
            batch.clear();
        }

        System.out.println("RAG index build completed: " + indexName);
        System.out.println("Scanned: " + scannedCount);
        System.out.println("Skipped existing: " + skippedCount);
        System.out.println("Inserted: " + insertedCount);
    }

    private void logProgress(String indexName, int scannedCount, int total, int skippedCount, int insertedCount) {
        System.out.println(
                "RAG index=" + indexName
                        + ", scanned=" + scannedCount + "/" + total
                        + ", skipped=" + skippedCount
                        + ", inserted=" + insertedCount
        );
    }
}

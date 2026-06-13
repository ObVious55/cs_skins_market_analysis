package com.example.priceprediction.rag;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItemRagIndexService {

    private static final int BATCH_SIZE = 100;

    private final ItemRagDocumentBuilder documentBuilder;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreClient vectorStoreClient;

    public ItemRagIndexService(ItemRagDocumentBuilder documentBuilder,
                               EmbeddingClient embeddingClient,
                               VectorStoreClient vectorStoreClient) {
        this.documentBuilder = documentBuilder;
        this.embeddingClient = embeddingClient;
        this.vectorStoreClient = vectorStoreClient;
    }

    public void rebuildIndex() {
        List<ItemRagDocument> documents = documentBuilder.buildAllDocuments();
        System.out.println("准备写入向量数据库，RAG 文档数量：" + documents.size());

        List<VectorStoreClient.VectorRecord> batch = new ArrayList<>();

        int total = documents.size();
        int scannedCount = 0;
        int skippedCount = 0;
        int insertedCount = 0;

        for (ItemRagDocument doc : documents) {
            scannedCount++;

            if (vectorStoreClient.exists(doc.getDocId())) {
                skippedCount++;

                if (scannedCount % 100 == 0) {
                    System.out.println(
                            "已扫描：" + scannedCount + "/" + total
                                    + "，已跳过：" + skippedCount
                                    + "，待新增：" + insertedCount
                    );
                }

                continue;
            }

            List<Float> vector = embeddingClient.embed(doc.getContent());

            VectorStoreClient.VectorRecord record =
                    new VectorStoreClient.VectorRecord(
                            doc.getDocId(),
                            vector,
                            doc.getContent(),
                            doc.getMetadata()
                    );

            batch.add(record);
            insertedCount++;

            if (batch.size() >= BATCH_SIZE) {
                vectorStoreClient.upsertBatch(batch);
                batch.clear();

                System.out.println(
                        "已扫描：" + scannedCount + "/" + total
                                + "，已跳过：" + skippedCount
                                + "，已新增：" + insertedCount
                );
            }
        }

        if (!batch.isEmpty()) {
            vectorStoreClient.upsertBatch(batch);
            batch.clear();
        }

        System.out.println("CS 饰品 RAG 向量索引构建完成");
        System.out.println("扫描总数量：" + scannedCount);
        System.out.println("跳过已有数量：" + skippedCount);
        System.out.println("新增数量：" + insertedCount);
    }
}
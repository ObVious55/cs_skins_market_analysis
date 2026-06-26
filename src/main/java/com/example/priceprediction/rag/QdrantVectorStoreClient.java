package com.example.priceprediction.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class QdrantVectorStoreClient implements VectorStoreClient {

    private final RestTemplate restTemplate;

    @Value("${qdrant.url:http://localhost:6333}")
    private String qdrantUrl;

    @Value("${qdrant.family-collection-name:cs_item_family_rag}")
    private String familyCollectionName;

    @Value("${qdrant.vector-size:1024}")
    private int vectorSize;

    public QdrantVectorStoreClient(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @PostConstruct
    public void initCollection() {
        createCollectionIfNotExists(familyCollectionName);
    }

    private void createCollectionIfNotExists(String targetCollectionName) {
        if (targetCollectionName == null || targetCollectionName.isBlank()) {
            return;
        }

        String collectionName = targetCollectionName.trim();
        String url = qdrantUrl + "/collections/" + collectionName;

        try {
            restTemplate.getForObject(url, Map.class);
            System.out.println("Qdrant collection 已存在，跳过创建：" + collectionName);
            return;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() != 404) {
                System.err.println("检查 Qdrant collection 失败：" + e.getMessage());
                throw e;
            }
        }

        Map<String, Object> vectorsConfig = new HashMap<>();
        vectorsConfig.put("size", vectorSize);
        vectorsConfig.put("distance", "Cosine");

        Map<String, Object> body = new HashMap<>();
        body.put("vectors", vectorsConfig);

        try {
            restTemplate.put(url, body);
            System.out.println("Qdrant collection 创建成功：" + collectionName);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                System.out.println("Qdrant collection 已存在，忽略 409：" + collectionName);
                return;
            }

            System.err.println("创建 Qdrant collection 失败：" + e.getMessage());
            throw e;
        }
    }

    public void upsertFamilyBatch(List<VectorRecord> records) {
        upsertBatch(familyCollectionName, records);
    }

    private void upsertBatch(String targetCollectionName, List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String url = qdrantUrl + "/collections/" + targetCollectionName + "/points?wait=true";

        List<Map<String, Object>> points = new ArrayList<>();

        for (VectorRecord record : records) {
            Map<String, Object> payload = new HashMap<>();

            if (record.getMetadata() != null) {
                payload.putAll(record.getMetadata());
            }

            payload.put("content", record.getContent());

            Map<String, Object> point = new HashMap<>();
            point.put("id", stableNumericId(record.getId()));
            point.put("vector", record.getVector());
            point.put("payload", payload);

            points.add(point);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("points", points);

        restTemplate.put(url, body);
    }

    public List<VectorSearchResult> searchFamily(List<Float> queryVector, int topK) {
        return search(familyCollectionName, queryVector, topK);
    }

    @SuppressWarnings("unchecked")
    private List<VectorSearchResult> search(String targetCollectionName, List<Float> queryVector, int topK) {
        String url = qdrantUrl + "/collections/" + targetCollectionName + "/points/search";

        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);

        Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

        if (response == null || response.get("result") == null) {
            return List.of();
        }

        List<Map<String, Object>> resultList = (List<Map<String, Object>>) response.get("result");

        List<VectorSearchResult> results = new ArrayList<>();

        for (Map<String, Object> item : resultList) {
            Object id = item.get("id");
            Object scoreObj = item.get("score");

            double score = scoreObj instanceof Number number ? number.doubleValue() : 0.0;

            Map<String, Object> payload = (Map<String, Object>) item.get("payload");

            String content = payload == null ? null : String.valueOf(payload.get("content"));

            results.add(new VectorSearchResult(
                    String.valueOf(id),
                    score,
                    content,
                    payload
            ));
        }

        return results;
    }

    public boolean familyExists(String id) {
        return exists(familyCollectionName, id);
    }

    private boolean exists(String targetCollectionName, String id) {
        long pointId = stableNumericId(id);
        String url = qdrantUrl + "/collections/" + targetCollectionName + "/points/" + pointId;

        try {
            restTemplate.getForObject(url, Map.class);
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Qdrant 的 point id 支持 unsigned integer 或 UUID。
     * 你的 docId 例如 ITEM_FAMILY:AK-47|Inheritance 不是 UUID，
     * 所以这里先把字符串稳定 hash 成正整数。
     */
    private long stableNumericId(String id) {
        return Integer.toUnsignedLong(id.hashCode());
    }
}

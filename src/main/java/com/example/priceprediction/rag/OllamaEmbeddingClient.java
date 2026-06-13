package com.example.priceprediction.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${embedding.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${embedding.ollama.model:mxbai-embed-large}")
    private String model;

    @Override
    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        String url = ollamaUrl + "/api/embed";

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", text == null ? "" : text
        );

        Map<String, Object> response = restTemplate.postForObject(
                url,
                requestBody,
                Map.class
        );

        if (response == null || response.get("embeddings") == null) {
            throw new IllegalStateException("Ollama embedding 响应为空");
        }

        List<Object> embeddings = (List<Object>) response.get("embeddings");

        if (embeddings.isEmpty()) {
            throw new IllegalStateException("Ollama embedding 结果为空");
        }

        Object first = embeddings.get(0);

        if (!(first instanceof List<?> firstEmbedding)) {
            throw new IllegalStateException("Ollama embedding 响应格式异常");
        }

        List<Float> result = new ArrayList<>(firstEmbedding.size());

        for (Object value : firstEmbedding) {
            if (value instanceof Number number) {
                result.add(number.floatValue());
            }
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Ollama embedding 向量为空");
        }

        return result;
    }
}
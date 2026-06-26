package com.example.priceprediction.rag;

import java.util.List;
import java.util.Map;

public interface VectorStoreClient {
    class VectorRecord {
        private String id;
        private List<Float> vector;
        private String content;
        private Map<String, Object> metadata;

        public VectorRecord(String id, List<Float> vector, String content, Map<String, Object> metadata) {
            this.id = id;
            this.vector = vector;
            this.content = content;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public List<Float> getVector() {
            return vector;
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }

    class VectorSearchResult {
        private String id;
        private double score;
        private String content;
        private Map<String, Object> metadata;

        public VectorSearchResult(String id, double score, String content, Map<String, Object> metadata) {
            this.id = id;
            this.score = score;
            this.content = content;
            this.metadata = metadata;
        }

        public String getId() {
            return id;
        }

        public double getScore() {
            return score;
        }

        public String getContent() {
            return content;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}

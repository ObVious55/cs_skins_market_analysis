package com.example.priceprediction.rag;

import java.util.List;

public interface EmbeddingClient {

    List<Float> embed(String text);
}
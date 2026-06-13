package com.example.priceprediction.controller;

import com.example.priceprediction.rag.ItemRagRetriever;
import com.example.priceprediction.rag.VectorStoreClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ItemRagTestController {

    private final ItemRagRetriever itemRagRetriever;

    public ItemRagTestController(ItemRagRetriever itemRagRetriever) {
        this.itemRagRetriever = itemRagRetriever;
    }

    @GetMapping("/rag/search")
    public List<VectorStoreClient.VectorSearchResult> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int topK
    ) {
        return itemRagRetriever.retrieve(q, topK);
    }
}
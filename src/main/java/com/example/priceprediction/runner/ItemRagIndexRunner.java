package com.example.priceprediction.runner;

import com.example.priceprediction.rag.ItemRagIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ItemRagIndexRunner implements CommandLineRunner {

    private final ItemRagIndexService itemRagIndexService;

    @Value("${cs.item.rag-index-enabled:false}")
    private boolean enabled;

    @Value("${cs.item.family-rag-index-enabled:false}")
    private boolean familyEnabled;

    public ItemRagIndexRunner(ItemRagIndexService itemRagIndexService) {
        this.itemRagIndexService = itemRagIndexService;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            System.out.println("CS item RAG index build is disabled.");
            return;
        }

        if (!familyEnabled) {
            System.out.println("CS item RAG index build is enabled, but no index target is selected.");
            return;
        }

        System.out.println("Start building CS item family RAG index.");
        itemRagIndexService.rebuildFamilyIndex();
    }
}

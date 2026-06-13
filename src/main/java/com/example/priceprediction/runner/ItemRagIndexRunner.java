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

    public ItemRagIndexRunner(ItemRagIndexService itemRagIndexService) {
        this.itemRagIndexService = itemRagIndexService;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            System.out.println("CS 饰品 RAG 向量索引构建未开启");
            return;
        }

        System.out.println("开始构建 CS 饰品 RAG 向量索引");
        itemRagIndexService.rebuildIndex();
    }
}
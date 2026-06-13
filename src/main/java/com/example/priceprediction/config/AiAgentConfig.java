package com.example.priceprediction.config;

import com.example.priceprediction.component.AiTools;
import com.example.priceprediction.service.InventoryAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAgentConfig {

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .temperature(0.7)
                .build();
    }

    @Bean
    public InventoryAgent inventoryAgent(
            ChatLanguageModel chatLanguageModel,
            AiTools tools,
            ChatMemoryProvider chatMemoryProvider
    ) {
        return AiServices.builder(InventoryAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(tools)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .build();
    }
}
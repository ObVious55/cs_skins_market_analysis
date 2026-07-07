package com.example.priceprediction.config;

import com.example.priceprediction.component.AiTools;
import com.example.priceprediction.service.InventoryAgent;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiAgentConfig {

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.model-name:deepseek-v4-flash}")
    private String agentModelName;

    @Value("${app.openai.temperature:0.1}")
    private Double agentTemperature;

    @Value("${app.openai.intent-model-name:deepseek-v4-flash}")
    private String intentModelName;

    @Value("${app.openai.intent-temperature:0.0}")
    private Double intentTemperature;

    @Bean("agentChatLanguageModel")
    public ChatLanguageModel agentChatLanguageModel() {
        return buildChatLanguageModel(agentModelName, agentTemperature);
    }

    @Bean("intentChatLanguageModel")
    public ChatLanguageModel intentChatLanguageModel() {
        return buildChatLanguageModel(intentModelName, intentTemperature);
    }

    private ChatLanguageModel buildChatLanguageModel(String modelName, Double temperature) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean
    public InventoryAgent inventoryAgent(
            @Qualifier("agentChatLanguageModel") ChatLanguageModel chatLanguageModel,
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
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }
}

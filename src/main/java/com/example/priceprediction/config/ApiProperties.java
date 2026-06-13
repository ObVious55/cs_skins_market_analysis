package com.example.priceprediction.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app") // 顶层前缀只写 app
public class ApiProperties {

    // 对应 app.csqaq
    private Csqaq csqaq = new Csqaq();

    // 对应 app.openai
    private OpenAi openai = new OpenAi();

    // 对应 app.steam
    private Steam steam = new Steam();

    // 内部类：管理 CSQAQ 的配置
    @Data
    public static class Csqaq {
        private String baseUrl;
        private String apiToken;
        private String klineBaseUrl;
        private String klineApiToken;
    }

    // 内部类：管理 OpenAI/DeepSeek 的配置
    @Data
    public static class OpenAi {
        private String baseUrl;
        private String apiKey;
    }

    // 内部类：管理 Steam 的配置
    @Data
    public static class Steam {
        private String apiKey;
    }
}

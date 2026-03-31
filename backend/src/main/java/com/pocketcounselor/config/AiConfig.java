package com.pocketcounselor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiConfig {

    @Value("${ai.mode:mock}")
    private String mode;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${gemini.base.url:https://generativelanguage.googleapis.com}")
    private String baseUrl;

    @Value("${gemini.timeout.seconds:30}")
    private int timeoutSeconds;

    public boolean isMockMode() {
        return "mock".equalsIgnoreCase(mode);
    }

    public String getMode() { return mode; }

    public String getApiKey() { return apiKey; }

    public boolean isApiKeyLoaded() {
        return apiKey != null && !apiKey.isBlank() && !"YOUR_KEY_HERE".equals(apiKey);
    }

    public String getModel() { return model; }

    public String getBaseUrl() { return baseUrl; }

    public int getTimeoutSeconds() { return timeoutSeconds; }

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}

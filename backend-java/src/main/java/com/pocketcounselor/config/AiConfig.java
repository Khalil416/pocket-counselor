package com.pocketcounselor.config;

import com.pocketcounselor.ai.AiClient;
import com.pocketcounselor.ai.MockAiClient;
import com.pocketcounselor.ai.RealAiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Bean
    public AiClient aiClient(MockAiClient mockAiClient, RealAiClient realAiClient) {
        String mode = System.getenv().getOrDefault("AI_MODE", "real").toLowerCase();
        return "real".equals(mode) ? realAiClient : mockAiClient;
    }
}

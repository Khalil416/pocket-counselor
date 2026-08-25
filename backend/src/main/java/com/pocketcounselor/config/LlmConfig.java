package com.pocketcounselor.config;

import com.pocketcounselor.llm.AnthropicClient;
import com.pocketcounselor.llm.GeminiClient;
import com.pocketcounselor.llm.LlmClient;
import com.pocketcounselor.llm.OpenAiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Bean wiring for the LLM provider layer.
 *
 * <p>All three clients are registered under bean names matching the accepted
 * {@code ai.provider} values, so {@code LlmClientResolver} can select one by
 * looking it up in the injected {@code Map<String, LlmClient>}. Constructing a
 * client performs no I/O and requires no API key -- providers you are not using
 * cost nothing beyond an idle object.
 */
@Configuration
public class LlmConfig {

    @Bean(AiConfig.GEMINI)
    public LlmClient geminiClient(AiConfig aiConfig) {
        return new GeminiClient(aiConfig.getGemini(), webClient(aiConfig.getGemini().getBaseUrl()));
    }

    @Bean(AiConfig.OPENAI)
    public LlmClient openaiClient(AiConfig aiConfig) {
        return new OpenAiClient(aiConfig.getOpenai(), webClient(aiConfig.getOpenai().getBaseUrl()));
    }

    @Bean(AiConfig.ANTHROPIC)
    public LlmClient anthropicClient(AiConfig aiConfig) {
        return new AnthropicClient(aiConfig.getAnthropic(), webClient(aiConfig.getAnthropic().getBaseUrl()));
    }

    private static WebClient webClient(String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}

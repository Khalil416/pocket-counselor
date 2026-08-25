package com.pocketcounselor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketcounselor.config.AiConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI: {@code POST /v1/chat/completions}, bearer-token auth,
 * body {@code messages[]} plus {@code temperature},
 * response {@code choices[0].message.content}.
 */
public class OpenAiClient extends AbstractLlmClient {

    private final AiConfig.OpenAi settings;

    public OpenAiClient(AiConfig.OpenAi settings, WebClient webClient) {
        super(AiConfig.OPENAI, webClient, settings.getTimeoutSeconds());
        this.settings = settings;
    }

    @Override
    public String complete(String prompt, double temperature) {
        String model = settings.getModel();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        // GPT-5-class reasoning models reject any non-default temperature with
        // unsupported_value. gpt-4o-mini and similar accept it.
        if (settings.isSendTemperature()) {
            requestBody.put("temperature", temperature);
        }
        if (settings.isJsonMode()) {
            // Requires the word "json" in the prompt; both prompt templates contain it.
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        log.info("[AI] OpenAI request started (model={})", model);

        String responseBody = postJson("/v1/chat/completions", requestBody,
                headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getApiKey()));
        JsonNode root = readTree(responseBody);

        if (root.has("error")) {
            String errorMsg = root.path("error").path("message").asText("Unknown OpenAI error");
            String errorType = root.path("error").path("type").asText("");
            log.error("[AI] OpenAI API error (type={}): {}", errorType, errorMsg);
            throw new LlmException(AiConfig.OPENAI, 0, "OpenAI API error: " + errorMsg);
        }

        String text = root
                .path("choices").path(0)
                .path("message").path("content").asText(null);

        if (text == null || text.isBlank()) {
            throw noText();
        }

        log.info("[AI] OpenAI request succeeded");
        return text;
    }
}

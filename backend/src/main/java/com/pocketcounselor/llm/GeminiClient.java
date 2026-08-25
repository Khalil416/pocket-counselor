package com.pocketcounselor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketcounselor.config.AiConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini: {@code POST /v1beta/models/{model}:generateContent?key=...},
 * body {@code contents[].parts[].text} plus {@code generationConfig},
 * response {@code candidates[0].content.parts[0].text}.
 */
public class GeminiClient extends AbstractLlmClient {

    private final AiConfig.Gemini settings;

    public GeminiClient(AiConfig.Gemini settings, WebClient webClient) {
        super(AiConfig.GEMINI, webClient, settings.getTimeoutSeconds());
        this.settings = settings;
    }

    @Override
    public String complete(String prompt, double temperature) {
        String model = settings.getModel();
        String uri = "/v1beta/models/" + model + ":generateContent?key=" + settings.getApiKey();

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "responseMimeType", "application/json"
                )
        );

        log.info("[AI] Gemini request started (model={})", model);

        String responseBody = postJson(uri, requestBody, headers -> { });
        JsonNode root = readTree(responseBody);

        // Gemini can also report failures in a 2xx body.
        if (root.has("error")) {
            int httpCode = root.path("error").path("code").asInt(0);
            String errorMsg = root.path("error").path("message").asText("Unknown Gemini error");
            log.error("[AI] Gemini API error (code={}): {}", httpCode, errorMsg);
            throw new LlmException(AiConfig.GEMINI, httpCode,
                    "Gemini API error (" + httpCode + "): " + errorMsg);
        }

        String text = root
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText(null);

        if (text == null || text.isBlank()) {
            throw noText();
        }

        log.info("[AI] Gemini request succeeded");
        return text;
    }
}

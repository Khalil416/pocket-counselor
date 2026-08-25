package com.pocketcounselor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketcounselor.config.AiConfig;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic: {@code POST /v1/messages}, {@code x-api-key} plus
 * {@code anthropic-version} headers, body {@code messages[]} with the required
 * {@code max_tokens}, response {@code content[0].text}.
 *
 * <p>Anthropic has no JSON-response mode, so output may arrive wrapped in
 * markdown fences -- {@code AiService.extractJson} strips those.
 *
 * <p>Two model-dependent details: current models reject {@code temperature}
 * (see {@code ai.anthropic.send-temperature}), and thinking-enabled models put a
 * thinking block first in {@code content[]}, so the text block is selected by
 * type rather than by position.
 */
public class AnthropicClient extends AbstractLlmClient {

    private final AiConfig.Anthropic settings;

    public AnthropicClient(AiConfig.Anthropic settings, WebClient webClient) {
        super(AiConfig.ANTHROPIC, webClient, settings.getTimeoutSeconds());
        this.settings = settings;
    }

    @Override
    public String complete(String prompt, double temperature) {
        String model = settings.getModel();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", settings.getMaxTokens());
        // Current models (Opus 5, Sonnet 5, Opus 4.7/4.8, Fable 5) reject sampling
        // parameters with a 400. Only send temperature for models that accept it.
        if (settings.isSendTemperature()) {
            requestBody.put("temperature", temperature);
        }
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));

        log.info("[AI] Anthropic request started (model={})", model);

        String responseBody = postJson("/v1/messages", requestBody, headers -> {
            headers.set("x-api-key", settings.getApiKey());
            headers.set("anthropic-version", settings.getVersion());
        });
        JsonNode root = readTree(responseBody);

        if ("error".equals(root.path("type").asText(null)) || root.has("error")) {
            String errorMsg = root.path("error").path("message").asText("Unknown Anthropic error");
            String errorType = root.path("error").path("type").asText("");
            log.error("[AI] Anthropic API error (type={}): {}", errorType, errorMsg);
            throw new LlmException(AiConfig.ANTHROPIC, 0, "Anthropic API error: " + errorMsg);
        }

        // content[] is a list of blocks and the first one is not necessarily the
        // text: thinking-enabled models lead with a thinking block. Take the first
        // block whose type is "text".
        String text = null;
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                String blockText = block.path("text").asText(null);
                if (blockText != null && !blockText.isBlank()) {
                    text = blockText;
                    break;
                }
            }
        }

        if (text == null) {
            throw noText();
        }

        log.info("[AI] Anthropic request succeeded");
        return text;
    }
}

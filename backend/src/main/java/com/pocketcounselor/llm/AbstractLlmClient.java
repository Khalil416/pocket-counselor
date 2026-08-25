package com.pocketcounselor.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Shared HTTP plumbing for the vendor clients: issue a JSON POST, surface the raw
 * body, and translate transport failures into {@link LlmException}.
 *
 * <p>Subclasses own the URI, auth mechanism, request body, and response envelope.
 */
abstract class AbstractLlmClient implements LlmClient {

    protected static final Logger log = LoggerFactory.getLogger(AbstractLlmClient.class);

    protected final ObjectMapper objectMapper = new ObjectMapper();

    private final String provider;
    private final WebClient webClient;
    private final int timeoutSeconds;

    protected AbstractLlmClient(String provider, WebClient webClient, int timeoutSeconds) {
        this.provider = provider;
        this.webClient = webClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getProvider() { return provider; }

    /**
     * POST {@code body} as JSON to {@code uri} and return the raw response body.
     *
     * <p>HTTP error statuses are mapped to {@link LlmException} carrying the status
     * code, so the rate-limit decorator can recognise a 429 without knowing which
     * provider produced it.
     */
    protected String postJson(String uri, Map<String, Object> body, Consumer<HttpHeaders> headers) {
        String responseBody;
        try {
            responseBody = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException e) {
            // Non-2xx: prefer the vendor's own error message when the body carries one.
            String detail = extractErrorMessage(e.getResponseBodyAsString());
            throw new LlmException(provider, e.getStatusCode().value(),
                    provider + " API error (" + e.getStatusCode().value() + "): "
                            + (detail != null ? detail : e.getStatusText()), e);
        } catch (WebClientRequestException e) {
            throw new LlmException(provider, 0,
                    provider + " request failed: " + e.getMessage(), e);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmException(provider, 0, "Empty response from " + provider + " API");
        }

        log.debug("[AI] {} raw response length={}", provider, responseBody.length());
        return responseBody;
    }

    protected JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new LlmException(provider, 0,
                    "Failed to parse " + provider + " response envelope", e);
        }
    }

    /**
     * Pull a human-readable message out of an error body. Gemini, OpenAI and
     * Anthropic all nest it under {@code error.message}.
     */
    private String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            return (msg == null || msg.isBlank()) ? null : msg;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    protected LlmException noText() {
        return new LlmException(provider, 0, "No text content in " + provider + " response");
    }
}

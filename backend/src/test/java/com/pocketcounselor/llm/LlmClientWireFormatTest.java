package com.pocketcounselor.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.config.AiConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each provider client gets two tests: one pinning the outgoing request shape
 * (path, auth mechanism, body), one pinning how the response envelope is read.
 */
class LlmClientWireFormatTest {

    private static final String PROMPT = "Score this answer and reply with json.";

    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private WebClient webClient() {
        return WebClient.builder().baseUrl("http://localhost:" + server.getPort()).build();
    }

    private void enqueueJson(String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private JsonNode requestBody(RecordedRequest request) throws Exception {
        return mapper.readTree(request.getBody().readUtf8());
    }

    // ----- Gemini -----

    private GeminiClient geminiClient() {
        AiConfig.Gemini settings = new AiConfig.Gemini();
        settings.setApiKey("test-gemini-key");
        settings.setModel("gemini-test");
        return new GeminiClient(settings, webClient());
    }

    @Test
    void gemini_sendsGenerateContentRequestWithKeyAsQueryParam() throws Exception {
        enqueueJson("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}");

        geminiClient().complete(PROMPT, 0.2);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1beta/models/gemini-test:generateContent?key=test-gemini-key", request.getPath());
        assertNull(request.getHeader("Authorization"), "Gemini authenticates by query param, not header");

        JsonNode body = requestBody(request);
        assertEquals(PROMPT, body.path("contents").path(0).path("parts").path(0).path("text").asText());
        assertEquals(0.2, body.path("generationConfig").path("temperature").asDouble(), 1e-9);
        assertEquals("application/json", body.path("generationConfig").path("responseMimeType").asText());
    }

    @Test
    void gemini_readsTextFromFirstCandidatePart() {
        enqueueJson("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"gemini said this\"}]}}]}");

        assertEquals("gemini said this", geminiClient().complete(PROMPT, 0.2));
    }

    // ----- OpenAI -----

    private OpenAiClient openAiClient() {
        AiConfig.OpenAi settings = new AiConfig.OpenAi();
        settings.setApiKey("test-openai-key");
        settings.setModel("gpt-test");
        return new OpenAiClient(settings, webClient());
    }

    @Test
    void openai_sendsChatCompletionWithBearerAuth() throws Exception {
        enqueueJson("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");

        openAiClient().complete(PROMPT, 0.7);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/chat/completions", request.getPath());
        assertEquals("Bearer test-openai-key", request.getHeader("Authorization"));

        JsonNode body = requestBody(request);
        assertEquals("gpt-test", body.path("model").asText());
        assertEquals("user", body.path("messages").path(0).path("role").asText());
        assertEquals(PROMPT, body.path("messages").path(0).path("content").asText());
        assertEquals(0.7, body.path("temperature").asDouble(), 1e-9);
        assertEquals("json_object", body.path("response_format").path("type").asText());
    }

    @Test
    void openai_readsContentFromFirstChoiceMessage() {
        enqueueJson("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"openai said this\"}}]}");

        assertEquals("openai said this", openAiClient().complete(PROMPT, 0.7));
    }

    // ----- Anthropic -----

    private AnthropicClient anthropicClient() {
        AiConfig.Anthropic settings = new AiConfig.Anthropic();
        settings.setApiKey("test-anthropic-key");
        settings.setModel("claude-test");
        settings.setMaxTokens(1234);
        return new AnthropicClient(settings, webClient());
    }

    @Test
    void anthropic_sendsMessagesRequestWithApiKeyAndVersionHeaders() throws Exception {
        enqueueJson("{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}");

        anthropicClient().complete(PROMPT, 0.7);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/messages", request.getPath());
        assertEquals("test-anthropic-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
        assertNull(request.getHeader("Authorization"), "Anthropic authenticates via x-api-key");

        JsonNode body = requestBody(request);
        assertEquals("claude-test", body.path("model").asText());
        assertEquals(1234, body.path("max_tokens").asInt(), "max_tokens is required by Anthropic");
        assertEquals(PROMPT, body.path("messages").path(0).path("content").asText());
        assertFalse(body.has("temperature"),
                "current Anthropic models reject sampling parameters with a 400");
    }

    @Test
    void anthropic_sendsTemperatureOnlyWhenExplicitlyEnabled() throws Exception {
        enqueueJson("{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}]}");

        AiConfig.Anthropic settings = new AiConfig.Anthropic();
        settings.setApiKey("test-anthropic-key");
        settings.setModel("claude-sonnet-4-6");
        settings.setSendTemperature(true);
        new AnthropicClient(settings, webClient()).complete(PROMPT, 0.7);

        JsonNode body = requestBody(server.takeRequest());
        assertEquals(0.7, body.path("temperature").asDouble(), 1e-9);
    }

    @Test
    void anthropic_readsFirstTextBlockSkippingThinking() {
        // Thinking-enabled models lead with a thinking block whose text is empty
        // under the default display setting -- the text block is not content[0].
        enqueueJson("{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"},"
                + "{\"type\":\"text\",\"text\":\"anthropic said this\"}]}");

        assertEquals("anthropic said this", anthropicClient().complete(PROMPT, 0.7));
    }
}

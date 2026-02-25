package com.pocketcounselor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.ScoringResult;
import com.pocketcounselor.model.Session;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class RealAiClient implements AiClient {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String callResponses(String systemPrompt, String userInput) {
        String apiKey = firstNonBlank(
                System.getenv("GEMINI_API_KEY"),
                System.getenv("GOOGLE_API_KEY")
        );
        if (apiKey == null) {
            throw new IllegalStateException("Gemini API key missing: set GEMINI_API_KEY or GOOGLE_API_KEY");
        }

        String model = firstNonBlank(System.getenv("GEMINI_MODEL"), "gemini-2.0-flash");
        try {
            Map<String, Object> payload = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", userInput))
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0,
                            "responseMimeType", "application/json"
                    )
            );

            HttpUrl url = HttpUrl.parse("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent");
            if (url == null) throw new IllegalStateException("Invalid Gemini URL");
            url = url.newBuilder().addQueryParameter("key", apiKey).build();

            Request req = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsBytes(payload), MediaType.parse("application/json")))
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                String body = resp.body() != null ? new String(resp.body().bytes(), StandardCharsets.UTF_8) : "";
                if (!resp.isSuccessful()) {
                    throw new RuntimeException("Gemini error " + resp.code() + ": " + body);
                }
                JsonNode root = mapper.readTree(body);
                JsonNode textNode = root.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts")
                        .path(0)
                        .path("text");

                if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                    throw new RuntimeException("Gemini response missing candidates[0].content.parts[0].text");
                }
                return textNode.asText();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @Override
    public ScoringResult score(String prompt, int questionId, String questionText, String answerText) {
        String user = "question_id=" + questionId + "\nquestion_text=" + questionText + "\nanswer_text=" + answerText;
        String json = callResponses(prompt, user);
        try { return mapper.readValue(json, ScoringResult.class); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public ResultsPayload results(String prompt, Session session) {
        String user = "counters=" + session.counters.questionsAnswered + "," + session.counters.questionsSkipped + "," + session.counters.invalidAnswers +
                "\npoints=" + session.points.total + "\ncheckpoint=" + session.checkpoints.reached + "\nskills=" + session.microSkillScores;
        String json = callResponses(prompt, user);
        try { return mapper.readValue(json, ResultsPayload.class); } catch (Exception e) { throw new RuntimeException(e); }
    }
}

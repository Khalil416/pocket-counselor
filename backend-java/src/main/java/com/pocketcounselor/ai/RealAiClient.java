package com.pocketcounselor.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.ScoringResult;
import com.pocketcounselor.model.Session;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class RealAiClient implements AiClient {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String callResponses(String systemPrompt, String userInput) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY missing");
        try {
            Map<String, Object> payload = Map.of(
                    "model", "gpt-4.1-mini",
                    "temperature", 0,
                    "input", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userInput)
                    ),
                    "text", Map.of("format", Map.of("type", "json_object"))
            );
            Request req = new Request.Builder()
                    .url("https://api.openai.com/v1/responses")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsBytes(payload), MediaType.parse("application/json")))
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) throw new RuntimeException("OpenAI error " + resp.code());
                JsonNode root = mapper.readTree(resp.body().bytes());
                return root.path("output").get(0).path("content").get(0).path("text").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

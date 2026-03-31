package com.pocketcounselor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.Session;
import com.pocketcounselor.store.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionControllerTest {

    /** Make @Async scoring execute synchronously for deterministic tests. */
    @TestConfiguration
    static class SyncScoringConfig {
        @Bean("scoringExecutor")
        @Primary
        public Executor scoringExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private SessionStore sessionStore;

    @BeforeEach
    void clearSessions() {
        sessionStore.clear();
    }

    // ── helpers ──────────────────────────────────────

    private String startSession() throws Exception {
        MvcResult r = mvc.perform(post("/api/session/start"))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString()).get("sessionId").asText();
    }

    private JsonNode submitAnswer(String sessionId, int questionId, String text) throws Exception {
        String body = mapper.writeValueAsString(
                java.util.Map.of("questionId", questionId, "answerText", text));
        MvcResult r = mvc.perform(post("/api/session/" + sessionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    // ── POST /api/session/start ─────────────────────

    @Test
    void start_returnsExpectedShape() throws Exception {
        mvc.perform(post("/api/session/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.question.id").isNumber())
                .andExpect(jsonPath("$.question.text").isString())
                .andExpect(jsonPath("$.question.questionNumber").value(1))
                .andExpect(jsonPath("$.question.expected_points").exists())
                .andExpect(jsonPath("$.session.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.session.counters").exists())
                .andExpect(jsonPath("$.session.points.total").value(0))
                .andExpect(jsonPath("$.session.checkpoints.reached").value(0))
                .andExpect(jsonPath("$.session.microSkillScores").isMap())
                .andExpect(jsonPath("$.canSkip").value(true));
    }

    // ── POST /api/session/{id}/answer — validation ──

    @Test
    void answer_rejectsTooShort() throws Exception {
        String sid = startSession();
        String body = mapper.writeValueAsString(
                java.util.Map.of("questionId", 1, "answerText", "short"));
        mvc.perform(post("/api/session/" + sid + "/answer")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("answer_too_short"));
    }

    @Test
    void answer_rejectsInvalidQuestionId() throws Exception {
        String sid = startSession();
        String body = mapper.writeValueAsString(
                java.util.Map.of("questionId", 99999, "answerText", "This is a valid long answer for testing purposes."));
        mvc.perform(post("/api/session/" + sid + "/answer")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_question_id"));
    }

    @Test
    void answer_rejectsMissingBody() throws Exception {
        String sid = startSession();
        String body = "{}";
        mvc.perform(post("/api/session/" + sid + "/answer")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_answer_payload"));
    }

    // ── POST /api/session/{id}/answer — happy path ──

    @Test
    void answer_returnsExpectedFieldsOnSuccess() throws Exception {
        String sid = startSession();
        // Question 1 should be the first question
        MvcResult startResult = mvc.perform(post("/api/session/start")).andReturn();
        JsonNode startNode = mapper.readTree(startResult.getResponse().getContentAsString());
        int qId = startNode.get("question").get("id").asInt();
        String startedSid = startNode.get("sessionId").asText();

        JsonNode answer = submitAnswer(startedSid, qId, "This is a comprehensive answer that should be long enough for the system to accept it properly.");

        // Verify all expected fields are present (matching Node contract)
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("nextQuestion"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("session"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("canSkip"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("finished"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("forcedResultsByLimit"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("lowDataQualityWarning"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("answerSubmissionIndex"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("lastScoredAnswerIndex"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("lastAnswerScored"));
        org.junit.jupiter.api.Assertions.assertTrue(answer.has("answeredCount"));

        // With sync executor + mock mode, scoring should complete immediately
        org.junit.jupiter.api.Assertions.assertEquals(1, answer.get("answerSubmissionIndex").asInt());
        org.junit.jupiter.api.Assertions.assertFalse(answer.get("finished").asBoolean());
    }

    // ── GET /api/session/{id}/state ─────────────────

    @Test
    void state_returnsExpectedShape() throws Exception {
        String sid = startSession();
        mvc.perform(get("/api/session/" + sid + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkpoint_reached_now").value(false))
                .andExpect(jsonPath("$.current_checkpoint").value(0))
                .andExpect(jsonPath("$.checkpoint").isEmpty())
                .andExpect(jsonPath("$.warning").isEmpty())
                .andExpect(jsonPath("$.low_data_quality_warning").value(false))
                .andExpect(jsonPath("$.scoring.answers_submitted").value(0))
                .andExpect(jsonPath("$.scoring.last_scored_answer_index").value(0))
                .andExpect(jsonPath("$.scoring.results_ready").value(false))
                .andExpect(jsonPath("$.session").exists());
    }

    @Test
    void state_clearsPendingCheckpointAndWarning() throws Exception {
        String sid = startSession();
        Session session = sessionStore.findById(sid);

        // Manually set pending values to simulate scoring completion
        session.setPendingWarning("invalid_soft");

        mvc.perform(get("/api/session/" + sid + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("invalid_soft"));

        // Second call should have cleared the warning
        mvc.perform(get("/api/session/" + sid + "/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").isEmpty());
    }

    // ── POST /api/session/{id}/skip ─────────────────

    @Test
    void skip_returnsExpectedShape() throws Exception {
        MvcResult startResult = mvc.perform(post("/api/session/start")).andReturn();
        JsonNode startNode = mapper.readTree(startResult.getResponse().getContentAsString());
        String sid = startNode.get("sessionId").asText();
        int qId = startNode.get("question").get("id").asInt();

        String body = mapper.writeValueAsString(java.util.Map.of("questionId", qId));
        mvc.perform(post("/api/session/" + sid + "/skip")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true))
                .andExpect(jsonPath("$.maxSkipsReached").isBoolean())
                .andExpect(jsonPath("$.nextQuestion").exists())
                .andExpect(jsonPath("$.session").exists())
                .andExpect(jsonPath("$.canSkip").isBoolean())
                .andExpect(jsonPath("$.finished").value(false));
    }

    @Test
    void skip_respectsMaxSkipLimit() throws Exception {
        MvcResult startResult = mvc.perform(post("/api/session/start")).andReturn();
        JsonNode startNode = mapper.readTree(startResult.getResponse().getContentAsString());
        String sid = startNode.get("sessionId").asText();

        // Skip 7 times to reach the limit
        for (int i = 0; i < 7; i++) {
            // Get current question from state
            MvcResult stateResult = mvc.perform(get("/api/session/" + sid + "/state")).andReturn();
            JsonNode stateNode = mapper.readTree(stateResult.getResponse().getContentAsString());
            // Use question ID from the ordering — we need the question the session is currently showing
            // For simplicity, just get next question number from start/skip response
            int queueIndex = stateNode.get("session").get("counters").get("questionsShown").asInt();

            // We need an actual question ID. Let's use incremental ones by reading the session
            Session session = sessionStore.findById(sid);
            if (session.getCurrentIndex() >= session.getQuestionQueue().size()) break;
            int qId = session.getQuestionQueue().get(session.getCurrentIndex()).getId();

            // Advance to get next question
            var nextQ = new com.pocketcounselor.dto.StartResponse.QuestionView();
            // Actually, just skip using the current question from the queue
            String body = mapper.writeValueAsString(java.util.Map.of("questionId", qId));
            MvcResult skipResult = mvc.perform(post("/api/session/" + sid + "/skip")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            // After getting the next question, we need the ID for the next skip
            JsonNode skipNode = mapper.readTree(skipResult.getResponse().getContentAsString());
            if (skipNode.get("nextQuestion").isNull()) break;
        }

        // 8th skip should fail
        Session session = sessionStore.findById(sid);
        if (session.getCurrentIndex() < session.getQuestionQueue().size()) {
            int qId = session.getQuestionQueue().get(session.getCurrentIndex()).getId();
            String body = mapper.writeValueAsString(java.util.Map.of("questionId", qId));
            mvc.perform(post("/api/session/" + sid + "/skip")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("max_skips_reached"));
        }
    }

    // ── GET /api/session/{id}/results — 409 ─────────

    @Test
    void results_returns409WhenNotReady() throws Exception {
        String sid = startSession();
        mvc.perform(get("/api/session/" + sid + "/results"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESULTS_NOT_READY"))
                .andExpect(jsonPath("$.readiness.minimum_answered_required").value(15));
    }

    // ── Results after readiness ─────────────────────

    @Test
    void results_returnsCachedResultsAfterReadiness() throws Exception {
        // Start session
        MvcResult startResult = mvc.perform(post("/api/session/start")).andReturn();
        JsonNode startNode = mapper.readTree(startResult.getResponse().getContentAsString());
        String sid = startNode.get("sessionId").asText();
        int firstQId = startNode.get("question").get("id").asInt();

        // Submit 15 answers with sync scoring (mock mode scores each as 8 points)
        int nextQId = firstQId;
        for (int i = 0; i < 15; i++) {
            JsonNode answerNode = submitAnswer(sid, nextQId,
                    "This is answer number " + (i + 1) + " and it is long enough for scoring purposes.");
            if (answerNode.has("nextQuestion") && !answerNode.get("nextQuestion").isNull()) {
                nextQId = answerNode.get("nextQuestion").get("id").asInt();
            }
        }

        // Now results should be ready
        MvcResult resultsResult = mvc.perform(get("/api/session/" + sid + "/results"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode results = mapper.readTree(resultsResult.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(results.has("profile_quality"));
        org.junit.jupiter.api.Assertions.assertTrue(results.has("overall_summary"));
        org.junit.jupiter.api.Assertions.assertTrue(results.has("categories"));
        org.junit.jupiter.api.Assertions.assertTrue(results.has("strongest_areas"));
        org.junit.jupiter.api.Assertions.assertTrue(results.has("growth_areas"));

        // Second call should return cached results
        mvc.perform(post("/api/session/" + sid + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_quality").value(results.get("profile_quality").asText()));
    }

    // ── 404 for missing session ─────────────────────

    @Test
    void missingSession_returns404() throws Exception {
        mvc.perform(get("/api/session/nonexistent-id/state"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("session_not_found"));
    }

    @Test
    void missingSession_results404HasNestedError() throws Exception {
        mvc.perform(get("/api/session/nonexistent-id/results"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isString());
    }

    // ── GET /api/session/{id}/answers ────────────────

    @Test
    void answers_returnsSessionAndAnswersList() throws Exception {
        MvcResult startResult = mvc.perform(post("/api/session/start")).andReturn();
        JsonNode startNode = mapper.readTree(startResult.getResponse().getContentAsString());
        String sid = startNode.get("sessionId").asText();
        int qId = startNode.get("question").get("id").asInt();

        submitAnswer(sid, qId, "This is a valid answer that should be long enough for the system.");

        mvc.perform(get("/api/session/" + sid + "/answers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session").exists())
                .andExpect(jsonPath("$.answers").isArray())
                .andExpect(jsonPath("$.answers[0].questionId").value(qId))
                .andExpect(jsonPath("$.answers[0].questionText").isString())
                .andExpect(jsonPath("$.answers[0].responseType").isString())
                .andExpect(jsonPath("$.answers[0].pointsEarned").isNumber());
    }

    // ── GET /api/health ─────────────────────────────

    @Test
    void health_returnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.ai_mode").value("mock"));
    }

    // ── GET /api/ai/status ──────────────────────────

    @Test
    void aiStatus_returnsExpectedFields() throws Exception {
        mvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("mock"))
                .andExpect(jsonPath("$.keyLoaded").isBoolean())
                .andExpect(jsonPath("$.promptFilesOk").value(true))
                .andExpect(jsonPath("$.model").isString())
                // Should NOT contain baseUrl (not in Node contract)
                .andExpect(jsonPath("$.baseUrl").doesNotExist());
    }
}

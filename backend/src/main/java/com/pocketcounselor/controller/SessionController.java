package com.pocketcounselor.controller;

import com.pocketcounselor.controller.GlobalExceptionHandler.BadRequestException;
import com.pocketcounselor.dto.*;
import com.pocketcounselor.model.CheckpointResult;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import com.pocketcounselor.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);
    private static final int MIN_ANSWER_LENGTH = 10;
    private static final int MAX_QUESTIONS_SHOWN = 35;

    private final SessionService sessionService;
    private final ScoringService scoringService;
    private final AiService aiService;
    private final CheckpointService checkpointService;

    public SessionController(SessionService sessionService, ScoringService scoringService,
                             AiService aiService, CheckpointService checkpointService) {
        this.sessionService = sessionService;
        this.scoringService = scoringService;
        this.aiService = aiService;
        this.checkpointService = checkpointService;
    }

    // ---- POST /api/session/start ----

    @PostMapping("/start")
    public StartResponse start() {
        Session session = sessionService.createSession();
        StartResponse.QuestionView firstQuestion = sessionService.getNextQuestion(session);
        return new StartResponse(
                session.getSessionId(),
                firstQuestion,
                sessionService.getClientSession(session),
                sessionService.canSkip(session)
        );
    }

    // ---- POST /api/session/{id}/answer ----

    @PostMapping("/{id}/answer")
    public AnswerResponse answer(@PathVariable String id, @RequestBody AnswerRequest body) {
        Session session = sessionService.getSessionOrThrow(id);

        // Validate request
        if (body.getQuestionId() == null) {
            throw new BadRequestException("invalid_answer_payload", "questionId and answer text are required");
        }

        String normalizedAnswer = body.resolveAnswerText();
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            throw new BadRequestException("invalid_answer_payload", "questionId and answer text are required");
        }

        normalizedAnswer = normalizedAnswer.trim();

        if (normalizedAnswer.length() < MIN_ANSWER_LENGTH) {
            throw new BadRequestException("answer_too_short",
                    "Please share a bit more \u2014 at least 10 characters.");
        }

        Question questionData = sessionService.getQuestionById(body.getQuestionId());
        if (questionData == null) {
            throw new BadRequestException("invalid_question_id", "Invalid question ID");
        }

        // Mark submission immediately
        int answerSubmissionIndex = sessionService.markAnswerSubmitted(session);

        // Fire async scoring (non-blocking)
        scoringService.fireScoring(session, questionData, normalizedAnswer, answerSubmissionIndex);

        // Advance to next question
        StartResponse.QuestionView nextQuestion = sessionService.getNextQuestion(session);
        boolean forceFinishAt35 = session.getCounters().getQuestionsShown() >= MAX_QUESTIONS_SHOWN;

        if (nextQuestion == null || forceFinishAt35) {
            sessionService.finishSession(session);
        }
        if (forceFinishAt35 && session.getCheckpoints().getReached() == 0) {
            session.setHasLowDataWarning(true);
        }

        AnswerResponse resp = new AnswerResponse();
        resp.setNextQuestion(nextQuestion);
        resp.setSession(sessionService.getClientSession(session));
        resp.setCanSkip(sessionService.canSkip(session));
        resp.setFinished(nextQuestion == null || forceFinishAt35);
        resp.setForcedResultsByLimit(forceFinishAt35);
        resp.setLowDataQualityWarning(forceFinishAt35 && session.getCheckpoints().getReached() == 0);
        resp.setAnswerSubmissionIndex(answerSubmissionIndex);
        resp.setLastScoredAnswerIndex(session.getLastScoredAnswerIndex());
        resp.setLastAnswerScored(session.getLastScoredAnswerIndex() >= answerSubmissionIndex);
        resp.setAnsweredCount(session.getCounters().getQuestionsAnswered());
        return resp;
    }

    // ---- GET /api/session/{id}/state ----

    @GetMapping("/{id}/state")
    public StateResponse state(@PathVariable String id) {
        Session session = sessionService.getSessionOrThrow(id);

        // One-time delivery: atomically read and clear pending fields
        CheckpointResult checkpoint;
        String warning;
        synchronized (session) {
            checkpoint = session.getPendingCheckpoint();
            warning = session.getPendingWarning();
            session.setPendingCheckpoint(null);
            session.setPendingWarning(null);
        }

        StateResponse resp = new StateResponse();
        resp.setCheckpointReachedNow(checkpoint != null);
        resp.setCurrentCheckpoint(session.getCheckpoints().getReached());
        resp.setCheckpoint(checkpoint);
        resp.setWarning(warning);
        resp.setLowDataQualityWarning(session.isHasLowDataWarning());
        resp.setScoring(new StateResponse.ScoringStatus(
                session.getLastAnswerSubmissionIndex(),
                session.getLastScoredAnswerIndex(),
                sessionService.isResultsReady(session)
        ));
        resp.setSession(sessionService.getClientSession(session));

        return resp;
    }

    // ---- POST /api/session/{id}/skip ----

    @PostMapping("/{id}/skip")
    public SkipResponse skip(@PathVariable String id, @RequestBody SkipRequest body) {
        Session session = sessionService.getSessionOrThrow(id);

        if (body.getQuestionId() == null) {
            throw new BadRequestException("missing_question_id", "questionId is required");
        }
        if (!sessionService.canSkip(session)) {
            throw new BadRequestException("max_skips_reached", "Maximum skips reached");
        }

        boolean maxSkipsReached = sessionService.skipQuestion(session, body.getQuestionId());
        StartResponse.QuestionView nextQuestion = sessionService.getNextQuestion(session);
        boolean forceFinishAt35 = session.getCounters().getQuestionsShown() >= MAX_QUESTIONS_SHOWN;

        if (nextQuestion == null || forceFinishAt35) {
            sessionService.finishSession(session);
        }
        if (forceFinishAt35 && session.getCheckpoints().getReached() == 0) {
            session.setHasLowDataWarning(true);
        }

        SkipResponse resp = new SkipResponse();
        resp.setSkipped(true);
        resp.setMaxSkipsReached(maxSkipsReached);
        resp.setNextQuestion(nextQuestion);
        resp.setSession(sessionService.getClientSession(session));
        resp.setCanSkip(sessionService.canSkip(session));
        resp.setFinished(nextQuestion == null || forceFinishAt35);
        return resp;
    }

    // ---- GET & POST /api/session/{id}/results ----

    @GetMapping("/{id}/results")
    public ResponseEntity<?> getResults(@PathVariable String id) {
        return handleResults(id);
    }

    @PostMapping("/{id}/results")
    public ResponseEntity<?> postResults(@PathVariable String id) {
        return handleResults(id);
    }

    private ResponseEntity<?> handleResults(String id) {
        Session session;
        try {
            session = sessionService.getSessionOrThrow(id);
        } catch (SessionService.SessionNotFoundException ex) {
            // Results endpoint uses nested error shape matching Node contract
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "SESSION_NOT_FOUND",
                            "message", "Session not found.")
            ));
        }

        // Return cached results if already generated
        if (session.isResultsRequested() && session.getResultsCache() != null) {
            return ResponseEntity.ok(session.getResultsCache());
        }

        // Wait briefly for async scoring to catch up (matches Node waitForResultsReadiness)
        if (!waitForResultsReady(session, 20000, 200)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", Map.of("code", "RESULTS_NOT_READY",
                            "message", "Results are not ready yet. Please retry once scoring catches up."),
                    "readiness", Map.of(
                            "minimum_answered_required", 10,
                            "answered_count", session.getCounters().getQuestionsAnswered(),
                            "last_scored_answer_index", session.getLastScoredAnswerIndex()
                    )
            ));
        }

        sessionService.finishSession(session);

        // Small delay before results call to let Gemini rate limits reset after scoring calls
        try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        // Retry up to 3 times with backoff (Gemini may 429 after scoring calls)
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResultsResponse aiResults = aiService.callResultsPrompt(session);

                if (!aiService.validateResultsResponse(aiResults)) {
                    log.warn("Results response failed validation for session {} (attempt {})", id, attempt);
                    lastError = new RuntimeException("AI_SCHEMA_ERROR");
                    Thread.sleep(2000);
                    continue;
                }

                session.setResultsRequested(true);
                session.setResultsCache(aiResults);
                return ResponseEntity.ok(aiResults);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                lastError = ie;
                break;
            } catch (Exception e) {
                log.warn("Results generation attempt {} failed for session {}: {}", attempt, id, e.getMessage());
                lastError = e;
                if (attempt < 3) {
                    try { Thread.sleep(5000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Results generation failed after retries for session {}: {}",
                id, lastError != null ? lastError.getMessage() : "unknown");
        return ResponseEntity.internalServerError().body(buildResultsError(
                "RESULTS_GENERATION_FAILED", session));
    }

    private boolean waitForResultsReady(Session session, long timeoutMs, long intervalMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start <= timeoutMs) {
            if (sessionService.isResultsReady(session)) return true;
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return sessionService.isResultsReady(session);
    }

    private Map<String, Object> buildResultsError(String code, Session session) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("profile_quality", "Basic");
        fallback.put("data_quality_note",
                "[DEMO / FALLBACK] These are placeholder results. Real analysis could not be generated.");
        fallback.put("overall_summary",
                "[DEMO DATA] This is a fallback profile shown because the real analysis could not be completed. "
                + "The scores and descriptions below are placeholder values and do not reflect your actual answers.");
        fallback.put("categories", List.of(
                Map.of("name", "Example Category A (Demo)", "score", 10, "label", "Weak",
                        "explanation", "[Placeholder] This is not a real score. Real results were unavailable."),
                Map.of("name", "Example Category B (Demo)", "score", 20, "label", "Weak",
                        "explanation", "[Placeholder] This is not a real score. Real results were unavailable."),
                Map.of("name", "Example Category C (Demo)", "score", 30, "label", "Basic",
                        "explanation", "[Placeholder] This is not a real score. Real results were unavailable."),
                Map.of("name", "Example Category D (Demo)", "score", 40, "label", "Basic",
                        "explanation", "[Placeholder] This is not a real score. Real results were unavailable.")
        ));
        fallback.put("strongest_areas", List.of(
                Map.of("skill_name", "Demo Skill 1", "reason",
                        "[Placeholder] This is demo data. Please retry for real results."),
                Map.of("skill_name", "Demo Skill 2", "reason",
                        "[Placeholder] This is demo data. Please retry for real results.")
        ));
        fallback.put("growth_areas", List.of(
                Map.of("skill_name", "Demo Growth Area 1", "reason",
                        "[Placeholder] This is demo data. Please retry for real results."),
                Map.of("skill_name", "Demo Growth Area 2", "reason",
                        "[Placeholder] This is demo data. Please retry for real results.")
        ));

        return Map.of(
                "error", Map.of("code", code, "message", "Results generation failed."),
                "fallback", fallback
        );
    }

    // ---- GET /api/session/{id}/answers ----

    @GetMapping("/{id}/answers")
    public Map<String, Object> answers(@PathVariable String id) {
        Session session = sessionService.getSessionOrThrow(id);

        List<Map<String, Object>> enrichedAnswers = session.getAnswers().stream()
                .map(entry -> {
                    Question q = sessionService.getQuestionById(entry.getQuestionId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("questionId", entry.getQuestionId());
                    m.put("questionText", q != null ? q.getText() : "Question " + entry.getQuestionId());
                    m.put("answerText", entry.getAnswerText() != null ? entry.getAnswerText() : "");
                    m.put("responseType", entry.getResponseType());
                    m.put("pointsEarned", entry.getPointsEarned());
                    return m;
                })
                .toList();

        return Map.of(
                "session", sessionService.getClientSession(session),
                "answers", enrichedAnswers
        );
    }
}

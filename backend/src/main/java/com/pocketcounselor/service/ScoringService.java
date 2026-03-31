package com.pocketcounselor.service;

import com.pocketcounselor.dto.ScoringResponse;
import com.pocketcounselor.model.CheckpointResult;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final AiService aiService;
    private final SessionService sessionService;
    private final CheckpointService checkpointService;

    public ScoringService(AiService aiService, SessionService sessionService,
                          CheckpointService checkpointService) {
        this.aiService = aiService;
        this.sessionService = sessionService;
        this.checkpointService = checkpointService;
    }

    @Async("scoringExecutor")
    public void fireScoring(Session session, Question question, String answerText, int answerSubmissionIndex) {
        try {
            ScoringResponse aiResponse;
            try {
                aiResponse = aiService.callScoringPrompt(question, answerText);
            } catch (Exception firstAttempt) {
                log.warn("[AI] scoring first attempt failed: {}, retrying after delay", firstAttempt.getMessage());
                try {
                    Thread.sleep(2000); // backoff before retry to avoid 429 rate limit
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    aiResponse = aiService.callScoringPrompt(question, answerText);
                } catch (Exception secondAttempt) {
                    log.error("[AI] scoring failed after retry: {}", secondAttempt.getMessage());
                    synchronized (session) {
                        sessionService.markAiFailure(session, question, answerText);
                        sessionService.markAnswerScored(session, answerSubmissionIndex);
                    }
                    return;
                }
            }

            boolean valid = aiService.validateScoringResponse(aiResponse);
            if (!valid) {
                log.warn("[AI] scoring response failed validation for question {} (responseType={}, totalPoints={}, skills={})",
                        question.getId(), aiResponse.getResponseType(), aiResponse.getTotalPoints(),
                        aiResponse.getSkillsDetected() == null ? "null" : aiResponse.getSkillsDetected().size());
                aiResponse.setResponseType("invalid_schema");
            }

            synchronized (session) {
                boolean applied = sessionService.applyAiScoring(session, question, answerText, aiResponse);
                log.info("[AI] scoring applied for Q{}: valid={}, sessionTotal={}",
                        question.getId(), applied, session.getPoints().getTotal());

                sessionService.markAnswerScored(session, answerSubmissionIndex);

                if (!applied) return;

                CheckpointResult checkpoint = checkpointService.checkForCheckpoint(session);
                if (checkpoint != null) {
                    session.setPendingCheckpoint(checkpoint);
                }

                int invalidCount = session.getCounters().getInvalidAnswers();
                if (invalidCount >= 7) {
                    session.setPendingWarning("invalid_hard");
                } else if (invalidCount >= 4) {
                    session.setPendingWarning("invalid_soft");
                }
            }

            log.debug("[AI] scoring complete for question {} (submission #{})", question.getId(), answerSubmissionIndex);
        } catch (Exception e) {
            log.error("[AI] unexpected error during scoring for question {}", question.getId(), e);
            synchronized (session) {
                sessionService.markAiFailure(session, question, answerText);
                sessionService.markAnswerScored(session, answerSubmissionIndex);
            }
        }
    }
}

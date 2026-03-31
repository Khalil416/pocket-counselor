package com.pocketcounselor.service;

import com.pocketcounselor.config.DataLoader;
import com.pocketcounselor.dto.ClientSession;
import com.pocketcounselor.dto.ScoringResponse;
import com.pocketcounselor.dto.StartResponse;
import com.pocketcounselor.model.AnswerRecord;
import com.pocketcounselor.model.Microskill;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import com.pocketcounselor.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int MAX_SKIPS = 7;
    private static final int MAX_QUESTIONS_SHOWN = 35;
    private static final int MIN_ANSWERED_FOR_RESULTS = 10;

    private final SessionStore sessionStore;
    private final DataLoader dataLoader;

    public SessionService(SessionStore sessionStore, DataLoader dataLoader) {
        this.sessionStore = sessionStore;
        this.dataLoader = dataLoader;
    }

    public Session createSession() {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setStartedAt(Instant.now().toString());
        session.setMicroSkillScores(buildEmptyScores());
        session.setQuestionQueue(new ArrayList<>(dataLoader.getTierSortedQuestions()));
        sessionStore.save(session);
        return session;
    }

    public Session getSessionOrThrow(String sessionId) {
        Session session = sessionStore.findById(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return session;
    }

    public ClientSession getClientSession(Session session) {
        ClientSession cs = new ClientSession();
        cs.setSessionId(session.getSessionId());
        cs.setStartedAt(session.getStartedAt());

        Session.Counters c = session.getCounters();
        cs.setCounters(new ClientSession.CountersView(
                c.getQuestionsShown(), c.getQuestionsAnswered(),
                c.getQuestionsSkipped(), c.getInvalidAnswers()));

        cs.setPoints(new ClientSession.PointsView(session.getPoints().getTotal()));
        cs.setCheckpoints(new ClientSession.CheckpointsView(session.getCheckpoints().getReached()));
        cs.setMicroSkillScores(session.getMicroSkillScores());
        return cs;
    }

    public StartResponse.QuestionView getNextQuestion(Session session) {
        if (session.isFinished()
                || session.getCounters().getQuestionsShown() >= MAX_QUESTIONS_SHOWN
                || session.getCurrentIndex() >= session.getQuestionQueue().size()) {
            return null;
        }
        Question q = session.getQuestionQueue().get(session.getCurrentIndex());
        session.setCurrentIndex(session.getCurrentIndex() + 1);
        session.getCounters().setQuestionsShown(session.getCounters().getQuestionsShown() + 1);
        return new StartResponse.QuestionView(q.getId(), q.getText(),
                session.getCounters().getQuestionsShown(), q.getExpectedPoints());
    }

    public int markAnswerSubmitted(Session session) {
        session.setLastAnswerSubmissionIndex(session.getLastAnswerSubmissionIndex() + 1);
        return session.getLastAnswerSubmissionIndex();
    }

    public void markAnswerScored(Session session, int answerIndex) {
        if (answerIndex > session.getLastScoredAnswerIndex()) {
            session.setLastScoredAnswerIndex(answerIndex);
        }
    }

    /**
     * Apply AI scoring to a session. Returns true if scoring was valid and applied.
     */
    public boolean applyAiScoring(Session session, Question question, String answerText, ScoringResponse aiResponse) {
        if (aiResponse == null) {
            markAiFailure(session, question, answerText);
            return false;
        }

        String type = aiResponse.getResponseType();

        if ("valid".equals(type)) {
            for (ScoringResponse.SkillDetection sd : aiResponse.getSkillsDetected()) {
                session.getMicroSkillScores().merge(sd.getSkillId(), sd.getPoints(), Integer::sum);
            }
            session.getPoints().setTotal(session.getPoints().getTotal() + aiResponse.getTotalPoints());
            session.getCounters().setQuestionsAnswered(session.getCounters().getQuestionsAnswered() + 1);
            session.getAnswers().add(new AnswerRecord(question.getId(), answerText, "scored", "valid", aiResponse.getTotalPoints()));
            return true;
        }

        if ("invalid".equals(type)) {
            session.getMicroSkillScores().merge("INVALID", 1, Integer::sum);
            session.getCounters().setInvalidAnswers(session.getCounters().getInvalidAnswers() + 1);
            session.getCounters().setQuestionsAnswered(session.getCounters().getQuestionsAnswered() + 1);
            session.getAnswers().add(new AnswerRecord(question.getId(), answerText, "scored", "invalid", 0));
            return true;
        }

        if ("skipped".equals(type)) {
            session.getAnswers().add(new AnswerRecord(question.getId(), answerText, "scored", "skipped", 0));
            return true;
        }

        // Unknown response type — treat as schema error
        session.getAnswers().add(new AnswerRecord(question.getId(), answerText, "ai_invalid_schema", "invalid_schema", 0));
        return false;
    }

    public void markAiFailure(Session session, Question question, String answerText) {
        session.getCounters().setQuestionsAnswered(session.getCounters().getQuestionsAnswered() + 1);
        session.getAnswers().add(new AnswerRecord(question.getId(), answerText, "ai_failed", "ai_failed", 0));
    }

    /**
     * Skip a question. Returns true if max skips reached.
     */
    public boolean skipQuestion(Session session, int questionId) {
        session.getCounters().setQuestionsSkipped(session.getCounters().getQuestionsSkipped() + 1);
        session.getAnswers().add(new AnswerRecord(questionId, "", "skipped", "skipped", 0));
        session.getSkippedQuestionIds().add(questionId);

        // Promote backup question if available
        Question backup = session.getQuestionQueue().stream()
                .filter(q -> q.getBackupFor() != null
                        && q.getBackupFor() == questionId
                        && !session.getSkippedQuestionIds().contains(q.getId()))
                .findFirst()
                .orElse(null);

        if (backup != null) {
            int idx = session.getQuestionQueue().indexOf(backup);
            if (idx > session.getCurrentIndex()) {
                session.getQuestionQueue().remove(idx);
                session.getQuestionQueue().add(session.getCurrentIndex(), backup);
            }
        }

        return session.getCounters().getQuestionsSkipped() >= MAX_SKIPS;
    }

    public boolean canSkip(Session session) {
        return session.getCounters().getQuestionsSkipped() < MAX_SKIPS;
    }

    public boolean isResultsReady(Session session) {
        return session.getCounters().getQuestionsAnswered() >= MIN_ANSWERED_FOR_RESULTS
                && session.getLastScoredAnswerIndex() >= MIN_ANSWERED_FOR_RESULTS;
    }

    public void finishSession(Session session) {
        session.setFinished(true);
    }

    public Question getQuestionById(int questionId) {
        return dataLoader.getQuestionsById().get(questionId);
    }

    // ----- private helpers -----

    private Map<String, Integer> buildEmptyScores() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Microskill ms : dataLoader.getMicroskills()) {
            scores.put(ms.getId(), 0);
        }
        scores.put("INVALID", 0);
        return scores;
    }

    // ----- custom exception -----

    public static class SessionNotFoundException extends RuntimeException {
        private final String sessionId;

        public SessionNotFoundException(String sessionId) {
            super("Session not found: " + sessionId);
            this.sessionId = sessionId;
        }

        public String getSessionId() { return sessionId; }
    }
}

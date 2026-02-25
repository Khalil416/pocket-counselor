package com.pocketcounselor.service;

import com.pocketcounselor.ai.AiClient;
import com.pocketcounselor.model.*;
import com.pocketcounselor.store.SessionStore;
import com.pocketcounselor.validation.AiValidationService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class SessionService {
    private final DataRepository data;
    private final SessionStore store;
    private final AiClient aiClient;
    private final AiValidationService validation;
    private final String scoringPrompt;
    private final String resultsPrompt;

    public SessionService(DataRepository data, SessionStore store, AiClient aiClient, AiValidationService validation) throws IOException {
        this.data = data; this.store = store; this.aiClient = aiClient; this.validation = validation;
        this.scoringPrompt = data.readPrompt("scoring.txt");
        this.resultsPrompt = data.readPrompt("results.txt");
    }

    public Session start() {
        Map<String,Integer> scores = new LinkedHashMap<>();
        data.getAllowedSkills().forEach(s -> scores.put(s,0));
        scores.put("INVALID", 0);
        Session s = new Session(UUID.randomUUID().toString(), new ArrayList<>(data.getAllQuestions()), scores);
        store.put(s);
        return s;
    }

    public Session get(String id) { return store.get(id); }

    public synchronized Question nextQuestion(Session s) {
        if (s.finished || s.counters.questionsShown >= 35 || s.currentIndex >= s.queue.size()) return null;
        Question q = s.queue.get(s.currentIndex++);
        s.counters.questionsShown++;
        return q;
    }

    public synchronized void skip(Session s, int questionId) {
        s.counters.questionsSkipped++;
        s.answers.add(new AnswerRecord(String.valueOf(questionId), "", "scored_skipped", "skipped", 0));
        Question current = s.queue.stream().filter(q -> q.id()==questionId).findFirst().orElse(null);
        if (current != null && current.backupQuestionId()!=null) {
            for (int i=s.currentIndex;i<s.queue.size();i++) {
                if (s.queue.get(i).id()==current.backupQuestionId()) {
                    Question backup = s.queue.remove(i);
                    s.queue.add(s.currentIndex, backup);
                    break;
                }
            }
        }
    }

    public void scoreAsync(Session s, Question q, String answerText) {
        s.answers.add(new AnswerRecord(String.valueOf(q.id()), answerText, "pending_scoring", "pending", 0));
        CompletableFuture.runAsync(() -> doScore(s, q, answerText));
    }

    private void doScore(Session s, Question q, String answerText) {
        ScoringResult res;
        try {
            try { s.scoringAttempts.incrementAndGet(); res = aiClient.score(scoringPrompt, q.id(), q.text(), answerText); }
            catch (Exception e) { s.scoringAttempts.incrementAndGet(); res = aiClient.score(scoringPrompt, q.id(), q.text(), answerText); }
        } catch (Exception e) {
            synchronized (s) { updateLastStatus(s, "ai_failed", "ai_failed", 0); }
            return;
        }

        synchronized (s) {
            if (!validation.validScoring(res, data.getAllowedSkills())) {
                System.err.println("AI schema error");
                updateLastStatus(s, "schema_invalid", "schema_invalid", 0);
                return;
            }

            switch (res.response_type()) {
                case "valid" -> {
                    res.skills_detected().forEach(sp -> s.microSkillScores.computeIfPresent(sp.skill_id(), (k,v)->v+sp.points()));
                    s.points.total += res.total_points();
                    s.counters.questionsAnswered++;
                    updateLastStatus(s, "scored_valid", "valid", res.total_points());
                }
                case "invalid" -> {
                    s.counters.invalidAnswers++;
                    s.counters.questionsAnswered++;
                    s.microSkillScores.compute("INVALID", (k,v)->v==null?1:v+1);
                    updateLastStatus(s, "scored_invalid", "invalid", 0);
                }
                default -> updateLastStatus(s, "scored_skipped", "skipped", 0);
            }
            checkCheckpoint(s);
        }
    }

    private void updateLastStatus(Session s, String status, String responseType, int points) {
        for (int i=s.answers.size()-1;i>=0;i--) {
            AnswerRecord a = s.answers.get(i);
            if ("pending_scoring".equals(a.status)) { a.status=status; a.responseType=responseType; a.pointsEarned=points; break; }
        }
    }

    private void checkCheckpoint(Session s) {
        int answered = s.counters.questionsAnswered;
        if (answered < 3 || answered % 3 != 0) return;
        int next = s.checkpoints.reached + 1;
        int total = s.points.total;
        if ((next==1 && total>=280 && answered>=10) || (next==2 && total>=420 && answered>=15)
            || (next==3 && total>=560 && answered>=20) || (next==4 && total>=700 && answered>=25)) {
            s.checkpoints.reached = next;
            s.pendingCheckpoint = next;
        }
    }

    public ResultsResponse results(Session s) {
        if (s.resultsGenerated && s.resultsCache != null) {
            return new ResultsResponse(s.resultsCache, s.counters.questionsShown >= 35 && s.checkpoints.reached==0);
        }
        ResultsPayload payload = aiClient.results(resultsPrompt, s);
        if (!validation.validResults(payload)) throw new IllegalStateException("Invalid results schema");
        s.resultsGenerated = true;
        s.resultsCache = payload;
        return new ResultsResponse(payload, s.counters.questionsShown >= 35 && s.checkpoints.reached==0);
    }

    public record ResultsResponse(ResultsPayload payload, boolean lowDataWarning) {}
}

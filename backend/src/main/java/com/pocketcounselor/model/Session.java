package com.pocketcounselor.model;

import com.pocketcounselor.dto.ResultsResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Session {

    public static class Counters {
        private int questionsShown;
        private int questionsAnswered;
        private int questionsSkipped;
        private int invalidAnswers;

        public Counters() {}

        public int getQuestionsShown() { return questionsShown; }
        public void setQuestionsShown(int questionsShown) { this.questionsShown = questionsShown; }

        public int getQuestionsAnswered() { return questionsAnswered; }
        public void setQuestionsAnswered(int questionsAnswered) { this.questionsAnswered = questionsAnswered; }

        public int getQuestionsSkipped() { return questionsSkipped; }
        public void setQuestionsSkipped(int questionsSkipped) { this.questionsSkipped = questionsSkipped; }

        public int getInvalidAnswers() { return invalidAnswers; }
        public void setInvalidAnswers(int invalidAnswers) { this.invalidAnswers = invalidAnswers; }
    }

    public static class Points {
        private int total;

        public Points() {}

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }

    public static class Checkpoints {
        private int reached;

        public Checkpoints() {}

        public int getReached() { return reached; }
        public void setReached(int reached) { this.reached = reached; }
    }

    private String sessionId;
    private String startedAt;
    private Counters counters = new Counters();
    private Points points = new Points();
    private Checkpoints checkpoints = new Checkpoints();
    private Map<String, Integer> microSkillScores = new LinkedHashMap<>();
    private List<AnswerRecord> answers = new ArrayList<>();
    private List<Question> questionQueue = new ArrayList<>();
    private int currentIndex;
    private Set<Integer> skippedQuestionIds = new HashSet<>();
    private volatile boolean finished;
    private volatile CheckpointResult pendingCheckpoint;
    private volatile String pendingWarning;
    private volatile boolean hasLowDataWarning;
    private volatile ResultsResponse resultsCache;
    private volatile boolean resultsRequested;
    private volatile int lastAnswerSubmissionIndex;
    private volatile int lastScoredAnswerIndex;

    public Session() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public Counters getCounters() { return counters; }
    public void setCounters(Counters counters) { this.counters = counters; }

    public Points getPoints() { return points; }
    public void setPoints(Points points) { this.points = points; }

    public Checkpoints getCheckpoints() { return checkpoints; }
    public void setCheckpoints(Checkpoints checkpoints) { this.checkpoints = checkpoints; }

    public Map<String, Integer> getMicroSkillScores() { return microSkillScores; }
    public void setMicroSkillScores(Map<String, Integer> microSkillScores) { this.microSkillScores = microSkillScores; }

    public List<AnswerRecord> getAnswers() { return answers; }
    public void setAnswers(List<AnswerRecord> answers) { this.answers = answers; }

    public List<Question> getQuestionQueue() { return questionQueue; }
    public void setQuestionQueue(List<Question> questionQueue) { this.questionQueue = questionQueue; }

    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }

    public Set<Integer> getSkippedQuestionIds() { return skippedQuestionIds; }
    public void setSkippedQuestionIds(Set<Integer> skippedQuestionIds) { this.skippedQuestionIds = skippedQuestionIds; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public CheckpointResult getPendingCheckpoint() { return pendingCheckpoint; }
    public void setPendingCheckpoint(CheckpointResult pendingCheckpoint) { this.pendingCheckpoint = pendingCheckpoint; }

    public String getPendingWarning() { return pendingWarning; }
    public void setPendingWarning(String pendingWarning) { this.pendingWarning = pendingWarning; }

    public boolean isHasLowDataWarning() { return hasLowDataWarning; }
    public void setHasLowDataWarning(boolean hasLowDataWarning) { this.hasLowDataWarning = hasLowDataWarning; }

    public ResultsResponse getResultsCache() { return resultsCache; }
    public void setResultsCache(ResultsResponse resultsCache) { this.resultsCache = resultsCache; }

    public boolean isResultsRequested() { return resultsRequested; }
    public void setResultsRequested(boolean resultsRequested) { this.resultsRequested = resultsRequested; }

    public int getLastAnswerSubmissionIndex() { return lastAnswerSubmissionIndex; }
    public void setLastAnswerSubmissionIndex(int lastAnswerSubmissionIndex) { this.lastAnswerSubmissionIndex = lastAnswerSubmissionIndex; }

    public int getLastScoredAnswerIndex() { return lastScoredAnswerIndex; }
    public void setLastScoredAnswerIndex(int lastScoredAnswerIndex) { this.lastScoredAnswerIndex = lastScoredAnswerIndex; }
}

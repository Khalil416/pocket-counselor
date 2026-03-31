package com.pocketcounselor.dto;

import java.util.Map;

public class ClientSession {

    private String sessionId;
    private String startedAt;
    private CountersView counters;
    private PointsView points;
    private CheckpointsView checkpoints;
    private Map<String, Integer> microSkillScores;

    public static class CountersView {
        private int questionsShown;
        private int questionsAnswered;
        private int questionsSkipped;
        private int invalidAnswers;

        public CountersView() {}

        public CountersView(int questionsShown, int questionsAnswered, int questionsSkipped, int invalidAnswers) {
            this.questionsShown = questionsShown;
            this.questionsAnswered = questionsAnswered;
            this.questionsSkipped = questionsSkipped;
            this.invalidAnswers = invalidAnswers;
        }

        public int getQuestionsShown() { return questionsShown; }
        public void setQuestionsShown(int questionsShown) { this.questionsShown = questionsShown; }

        public int getQuestionsAnswered() { return questionsAnswered; }
        public void setQuestionsAnswered(int questionsAnswered) { this.questionsAnswered = questionsAnswered; }

        public int getQuestionsSkipped() { return questionsSkipped; }
        public void setQuestionsSkipped(int questionsSkipped) { this.questionsSkipped = questionsSkipped; }

        public int getInvalidAnswers() { return invalidAnswers; }
        public void setInvalidAnswers(int invalidAnswers) { this.invalidAnswers = invalidAnswers; }
    }

    public static class PointsView {
        private int total;

        public PointsView() {}
        public PointsView(int total) { this.total = total; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
    }

    public static class CheckpointsView {
        private int reached;

        public CheckpointsView() {}
        public CheckpointsView(int reached) { this.reached = reached; }

        public int getReached() { return reached; }
        public void setReached(int reached) { this.reached = reached; }
    }

    public ClientSession() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public CountersView getCounters() { return counters; }
    public void setCounters(CountersView counters) { this.counters = counters; }

    public PointsView getPoints() { return points; }
    public void setPoints(PointsView points) { this.points = points; }

    public CheckpointsView getCheckpoints() { return checkpoints; }
    public void setCheckpoints(CheckpointsView checkpoints) { this.checkpoints = checkpoints; }

    public Map<String, Integer> getMicroSkillScores() { return microSkillScores; }
    public void setMicroSkillScores(Map<String, Integer> microSkillScores) { this.microSkillScores = microSkillScores; }
}

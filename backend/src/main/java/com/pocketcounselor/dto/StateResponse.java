package com.pocketcounselor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pocketcounselor.model.CheckpointResult;

public class StateResponse {

    @JsonProperty("checkpoint_reached_now")
    private boolean checkpointReachedNow;

    @JsonProperty("current_checkpoint")
    private int currentCheckpoint;

    private CheckpointResult checkpoint;
    private String warning;

    @JsonProperty("low_data_quality_warning")
    private boolean lowDataQualityWarning;

    private ScoringStatus scoring;
    private ClientSession session;

    public static class ScoringStatus {
        @JsonProperty("answers_submitted")
        private int answersSubmitted;

        @JsonProperty("last_scored_answer_index")
        private int lastScoredAnswerIndex;

        @JsonProperty("results_ready")
        private boolean resultsReady;

        public ScoringStatus() {}

        public ScoringStatus(int answersSubmitted, int lastScoredAnswerIndex, boolean resultsReady) {
            this.answersSubmitted = answersSubmitted;
            this.lastScoredAnswerIndex = lastScoredAnswerIndex;
            this.resultsReady = resultsReady;
        }

        public int getAnswersSubmitted() { return answersSubmitted; }
        public void setAnswersSubmitted(int answersSubmitted) { this.answersSubmitted = answersSubmitted; }

        public int getLastScoredAnswerIndex() { return lastScoredAnswerIndex; }
        public void setLastScoredAnswerIndex(int lastScoredAnswerIndex) { this.lastScoredAnswerIndex = lastScoredAnswerIndex; }

        public boolean isResultsReady() { return resultsReady; }
        public void setResultsReady(boolean resultsReady) { this.resultsReady = resultsReady; }
    }

    public StateResponse() {}

    public boolean isCheckpointReachedNow() { return checkpointReachedNow; }
    public void setCheckpointReachedNow(boolean checkpointReachedNow) { this.checkpointReachedNow = checkpointReachedNow; }

    public int getCurrentCheckpoint() { return currentCheckpoint; }
    public void setCurrentCheckpoint(int currentCheckpoint) { this.currentCheckpoint = currentCheckpoint; }

    public CheckpointResult getCheckpoint() { return checkpoint; }
    public void setCheckpoint(CheckpointResult checkpoint) { this.checkpoint = checkpoint; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }

    public boolean isLowDataQualityWarning() { return lowDataQualityWarning; }
    public void setLowDataQualityWarning(boolean lowDataQualityWarning) { this.lowDataQualityWarning = lowDataQualityWarning; }

    public ScoringStatus getScoring() { return scoring; }
    public void setScoring(ScoringStatus scoring) { this.scoring = scoring; }

    public ClientSession getSession() { return session; }
    public void setSession(ClientSession session) { this.session = session; }
}

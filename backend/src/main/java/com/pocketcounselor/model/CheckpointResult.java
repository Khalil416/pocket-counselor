package com.pocketcounselor.model;

public class CheckpointResult {

    private int level;
    private String label;
    private int questionsAnswered;
    private int totalPoints;
    private boolean autoShow;
    private boolean resultsReady;

    public CheckpointResult() {}

    public CheckpointResult(int level, String label, int questionsAnswered, int totalPoints, boolean autoShow, boolean resultsReady) {
        this.level = level;
        this.label = label;
        this.questionsAnswered = questionsAnswered;
        this.totalPoints = totalPoints;
        this.autoShow = autoShow;
        this.resultsReady = resultsReady;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getQuestionsAnswered() { return questionsAnswered; }
    public void setQuestionsAnswered(int questionsAnswered) { this.questionsAnswered = questionsAnswered; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public boolean isAutoShow() { return autoShow; }
    public void setAutoShow(boolean autoShow) { this.autoShow = autoShow; }

    public boolean isResultsReady() { return resultsReady; }
    public void setResultsReady(boolean resultsReady) { this.resultsReady = resultsReady; }
}

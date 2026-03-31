package com.pocketcounselor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Question {

    public static class ExpectedPoints {
        private int minimum;
        private int target;
        private int excellent;

        public ExpectedPoints() {}

        public int getMinimum() { return minimum; }
        public void setMinimum(int minimum) { this.minimum = minimum; }

        public int getTarget() { return target; }
        public void setTarget(int target) { this.target = target; }

        public int getExcellent() { return excellent; }
        public void setExcellent(int excellent) { this.excellent = excellent; }
    }

    private int id;
    private int tier;

    @JsonProperty("signal_level")
    private String signalLevel;

    @JsonProperty("expected_points")
    private ExpectedPoints expectedPoints;

    @JsonProperty("backup_for")
    private Integer backupFor;

    @JsonProperty("has_backup")
    private Boolean hasBackup;

    @JsonProperty("backup_question_id")
    private Integer backupQuestionId;

    private String text;

    public Question() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public String getSignalLevel() { return signalLevel; }
    public void setSignalLevel(String signalLevel) { this.signalLevel = signalLevel; }

    public ExpectedPoints getExpectedPoints() { return expectedPoints; }
    public void setExpectedPoints(ExpectedPoints expectedPoints) { this.expectedPoints = expectedPoints; }

    public Integer getBackupFor() { return backupFor; }
    public void setBackupFor(Integer backupFor) { this.backupFor = backupFor; }

    public Boolean getHasBackup() { return hasBackup; }
    public void setHasBackup(Boolean hasBackup) { this.hasBackup = hasBackup; }

    public Integer getBackupQuestionId() { return backupQuestionId; }
    public void setBackupQuestionId(Integer backupQuestionId) { this.backupQuestionId = backupQuestionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

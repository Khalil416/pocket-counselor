package com.pocketcounselor.model;

public class AnswerRecord {

    private int questionId;
    private String answerText;
    private String status;
    private String responseType;
    private int pointsEarned;

    public AnswerRecord() {}

    public AnswerRecord(int questionId, String answerText, String status, String responseType, int pointsEarned) {
        this.questionId = questionId;
        this.answerText = answerText;
        this.status = status;
        this.responseType = responseType;
        this.pointsEarned = pointsEarned;
    }

    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }
}

package com.pocketcounselor.model;

public class AnswerRecord {
    public String questionId;
    public String answerText;
    public String status;
    public String responseType;
    public int pointsEarned;

    public AnswerRecord(String questionId, String answerText, String status, String responseType, int pointsEarned) {
        this.questionId = questionId;
        this.answerText = answerText;
        this.status = status;
        this.responseType = responseType;
        this.pointsEarned = pointsEarned;
    }
}

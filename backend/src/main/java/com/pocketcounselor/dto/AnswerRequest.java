package com.pocketcounselor.dto;

public class AnswerRequest {

    private Integer questionId;
    private String answer;
    private String answerText;

    public AnswerRequest() {}

    public Integer getQuestionId() { return questionId; }
    public void setQuestionId(Integer questionId) { this.questionId = questionId; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    /** Returns the normalized answer text, preferring 'answer' over 'answerText'. */
    public String resolveAnswerText() {
        if (answer != null) return answer;
        return answerText;
    }
}

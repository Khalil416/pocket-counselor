package com.pocketcounselor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pocketcounselor.model.Question;

public class StartResponse {

    private String sessionId;
    private QuestionView question;
    private ClientSession session;
    private boolean canSkip;

    public static class QuestionView {
        private int id;
        private String text;
        private int questionNumber;

        @JsonProperty("expected_points")
        private Question.ExpectedPoints expectedPoints;

        public QuestionView() {}

        public QuestionView(int id, String text, int questionNumber, Question.ExpectedPoints expectedPoints) {
            this.id = id;
            this.text = text;
            this.questionNumber = questionNumber;
            this.expectedPoints = expectedPoints;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public int getQuestionNumber() { return questionNumber; }
        public void setQuestionNumber(int questionNumber) { this.questionNumber = questionNumber; }

        public Question.ExpectedPoints getExpectedPoints() { return expectedPoints; }
        public void setExpectedPoints(Question.ExpectedPoints expectedPoints) { this.expectedPoints = expectedPoints; }
    }

    public StartResponse() {}

    public StartResponse(String sessionId, QuestionView question, ClientSession session, boolean canSkip) {
        this.sessionId = sessionId;
        this.question = question;
        this.session = session;
        this.canSkip = canSkip;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public QuestionView getQuestion() { return question; }
    public void setQuestion(QuestionView question) { this.question = question; }

    public ClientSession getSession() { return session; }
    public void setSession(ClientSession session) { this.session = session; }

    public boolean isCanSkip() { return canSkip; }
    public void setCanSkip(boolean canSkip) { this.canSkip = canSkip; }
}

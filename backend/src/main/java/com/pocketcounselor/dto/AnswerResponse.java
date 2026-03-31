package com.pocketcounselor.dto;

public class AnswerResponse {

    private StartResponse.QuestionView nextQuestion;
    private ClientSession session;
    private boolean canSkip;
    private boolean finished;
    private boolean forcedResultsByLimit;
    private boolean lowDataQualityWarning;
    private int answerSubmissionIndex;
    private int lastScoredAnswerIndex;
    private boolean lastAnswerScored;
    private int answeredCount;

    public AnswerResponse() {}

    public StartResponse.QuestionView getNextQuestion() { return nextQuestion; }
    public void setNextQuestion(StartResponse.QuestionView nextQuestion) { this.nextQuestion = nextQuestion; }

    public ClientSession getSession() { return session; }
    public void setSession(ClientSession session) { this.session = session; }

    public boolean isCanSkip() { return canSkip; }
    public void setCanSkip(boolean canSkip) { this.canSkip = canSkip; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public boolean isForcedResultsByLimit() { return forcedResultsByLimit; }
    public void setForcedResultsByLimit(boolean forcedResultsByLimit) { this.forcedResultsByLimit = forcedResultsByLimit; }

    public boolean isLowDataQualityWarning() { return lowDataQualityWarning; }
    public void setLowDataQualityWarning(boolean lowDataQualityWarning) { this.lowDataQualityWarning = lowDataQualityWarning; }

    public int getAnswerSubmissionIndex() { return answerSubmissionIndex; }
    public void setAnswerSubmissionIndex(int answerSubmissionIndex) { this.answerSubmissionIndex = answerSubmissionIndex; }

    public int getLastScoredAnswerIndex() { return lastScoredAnswerIndex; }
    public void setLastScoredAnswerIndex(int lastScoredAnswerIndex) { this.lastScoredAnswerIndex = lastScoredAnswerIndex; }

    public boolean isLastAnswerScored() { return lastAnswerScored; }
    public void setLastAnswerScored(boolean lastAnswerScored) { this.lastAnswerScored = lastAnswerScored; }

    public int getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(int answeredCount) { this.answeredCount = answeredCount; }
}

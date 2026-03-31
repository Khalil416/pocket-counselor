package com.pocketcounselor.dto;

public class SkipResponse {

    private boolean skipped;
    private boolean maxSkipsReached;
    private StartResponse.QuestionView nextQuestion;
    private ClientSession session;
    private boolean canSkip;
    private boolean finished;

    public SkipResponse() {}

    public boolean isSkipped() { return skipped; }
    public void setSkipped(boolean skipped) { this.skipped = skipped; }

    public boolean isMaxSkipsReached() { return maxSkipsReached; }
    public void setMaxSkipsReached(boolean maxSkipsReached) { this.maxSkipsReached = maxSkipsReached; }

    public StartResponse.QuestionView getNextQuestion() { return nextQuestion; }
    public void setNextQuestion(StartResponse.QuestionView nextQuestion) { this.nextQuestion = nextQuestion; }

    public ClientSession getSession() { return session; }
    public void setSession(ClientSession session) { this.session = session; }

    public boolean isCanSkip() { return canSkip; }
    public void setCanSkip(boolean canSkip) { this.canSkip = canSkip; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
}

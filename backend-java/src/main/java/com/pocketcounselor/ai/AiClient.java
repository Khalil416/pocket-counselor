package com.pocketcounselor.ai;

import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.ScoringResult;
import com.pocketcounselor.model.Session;

public interface AiClient {
    ScoringResult score(String prompt, int questionId, String questionText, String answerText);
    ResultsPayload results(String prompt, Session session);
}

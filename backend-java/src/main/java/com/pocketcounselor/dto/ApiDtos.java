package com.pocketcounselor.dto;

import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.Session;

public class ApiDtos {
    public record AnswerRequest(Integer questionId, String answerText) {}
    public record SkipRequest(Integer questionId) {}
    public record StartResponse(String sessionId, Question question, Session session, boolean canSkip) {}
    public record AnswerResponse(Question nextQuestion, Session session, boolean canSkip, boolean finished, boolean forcedResultsByLimit, boolean lowDataQualityWarning) {}
    public record StateResponse(boolean checkpoint_reached_now, int current_checkpoint, Integer checkpoint, boolean low_data_quality_warning, Session session) {}
    public record ResultsResponse(ResultsPayload results, boolean low_data_quality_warning) {}
}

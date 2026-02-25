package com.pocketcounselor.ai;

import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.ScoringResult;
import com.pocketcounselor.model.Session;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockAiClient implements AiClient {
    @Override
    public ScoringResult score(String prompt, int questionId, String questionText, String answerText) {
        String v = answerText.toLowerCase();
        if (v.contains("[force_ai_fail]")) throw new RuntimeException("forced fail");
        if (v.contains("[force_schema_invalid]")) return new ScoringResult("valid", 7, List.of(new ScoringResult.SkillPoint("empathy", 8)));
        if (v.contains("[force_invalid]")) return new ScoringResult("invalid", 0, List.of());
        if (v.contains("[force_skipped]")) return new ScoringResult("skipped", 0, List.of());
        return new ScoringResult("valid", 8, List.of(new ScoringResult.SkillPoint("empathy", 5), new ScoringResult.SkillPoint("planning", 3)));
    }

    @Override
    public ResultsPayload results(String prompt, Session session) {
        return new ResultsPayload(
                session.checkpoints.reached >= 2 ? "Good" : "Basic",
                "Mock summary",
                List.of(new ResultsPayload.Category("Communication", 60, "Good", "Consistent communication indicators")),
                List.of(new ResultsPayload.SkillReason("Empathy", "Frequently detected")),
                List.of(new ResultsPayload.SkillReason("Numerical reasoning", "Less signal"))
        );
    }
}

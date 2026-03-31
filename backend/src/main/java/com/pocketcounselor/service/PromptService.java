package com.pocketcounselor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private String scoringTemplate;
    private String resultsTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws IOException {
        scoringTemplate = loadResource("prompts/scoring.txt");
        resultsTemplate = loadResource("prompts/results.txt");
    }

    private String loadResource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    public String buildScoringPrompt(Question question, String answerText) {
        Question.ExpectedPoints ep = question.getExpectedPoints();
        return scoringTemplate
                .replace("{question_text}", question.getText())
                .replace("{min}", String.valueOf(ep.getMinimum()))
                .replace("{target}", String.valueOf(ep.getTarget()))
                .replace("{excellent}", String.valueOf(ep.getExcellent()))
                .replace("{answer_text}", answerText);
    }

    public String buildResultsPrompt(Session session) {
        String scoresJson;
        try {
            scoresJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(session.getMicroSkillScores());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize micro-skill scores", e);
        }

        // DIAGNOSTIC: Show non-zero skill scores
        Map<String, Integer> nonZero = session.getMicroSkillScores().entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        log.info("[DIAG] Results prompt building. questionsAnswered={}, totalPoints={}, nonZeroSkills={}",
                session.getCounters().getQuestionsAnswered(),
                session.getPoints().getTotal(),
                nonZero);

        return resultsTemplate
                .replace("{questions_answered}", String.valueOf(session.getCounters().getQuestionsAnswered()))
                .replace("{questions_skipped}", String.valueOf(session.getCounters().getQuestionsSkipped()))
                .replace("{invalid_answers}", String.valueOf(session.getCounters().getInvalidAnswers()))
                .replace("{total_points}", String.valueOf(session.getPoints().getTotal()))
                .replace("{checkpoint_number}", String.valueOf(session.getCheckpoints().getReached()))
                .replace("{micro_skill_scores}", scoresJson);
    }

    public boolean isLoaded() {
        return scoringTemplate != null && !scoringTemplate.isBlank()
                && resultsTemplate != null && !resultsTemplate.isBlank();
    }
}

package com.pocketcounselor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.Microskill;
import com.pocketcounselor.model.Question;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<Question> questions;
    private List<Microskill> microskills;
    private Set<String> allowedSkillIds;
    private Map<Integer, Question> questionsById;
    private List<Question> tierSortedQuestions;

    @PostConstruct
    public void init() throws IOException {
        loadQuestions();
        loadMicroskills();
        buildDerivedData();
    }

    private void loadQuestions() throws IOException {
        try (InputStream is = new ClassPathResource("data/questions.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            List<Question> list = objectMapper.convertValue(
                    root.get("questions"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Question.class)
            );
            this.questions = Collections.unmodifiableList(list);
        }
    }

    private void loadMicroskills() throws IOException {
        try (InputStream is = new ClassPathResource("data/microskills.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            List<Microskill> list = objectMapper.convertValue(
                    root.get("microskills"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Microskill.class)
            );
            this.microskills = Collections.unmodifiableList(list);
        }
    }

    private void buildDerivedData() {
        this.allowedSkillIds = microskills.stream()
                .map(Microskill::getId)
                .collect(Collectors.toUnmodifiableSet());

        this.questionsById = questions.stream()
                .collect(Collectors.toUnmodifiableMap(Question::getId, q -> q));

        // Tier-sorted queue: tier 1 sorted by id, then tier 2, then tier 3
        List<Question> sorted = new ArrayList<>();
        for (int tier = 1; tier <= 3; tier++) {
            int t = tier;
            questions.stream()
                    .filter(q -> q.getTier() == t)
                    .sorted(Comparator.comparingInt(Question::getId))
                    .forEach(sorted::add);
        }
        this.tierSortedQuestions = Collections.unmodifiableList(sorted);
    }

    public List<Question> getQuestions() { return questions; }

    public List<Microskill> getMicroskills() { return microskills; }

    public Set<String> getAllowedSkillIds() { return allowedSkillIds; }

    public Map<Integer, Question> getQuestionsById() { return questionsById; }

    public List<Question> getTierSortedQuestions() { return tierSortedQuestions; }
}

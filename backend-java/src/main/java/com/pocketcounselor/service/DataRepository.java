package com.pocketcounselor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.Question;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class DataRepository {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Question> allQuestions;
    private final Set<String> allowedSkills;

    public DataRepository() throws IOException {
        Path dataDir = resolvePath("data");
        JsonNode questionsRoot = mapper.readTree(Files.readString(dataDir.resolve("questions.json")));
        JsonNode skillsRoot = mapper.readTree(Files.readString(dataDir.resolve("microskills.json")));

        List<Question> questions = new ArrayList<>();
        for (JsonNode n : questionsRoot.get("questions")) {
            Integer backup = n.hasNonNull("backup_question_id") ? n.get("backup_question_id").asInt() : null;
            questions.add(new Question(n.get("id").asInt(), n.get("tier").asInt(), n.get("text").asText(), backup));
        }
        questions.sort(Comparator.comparingInt(Question::tier).thenComparingInt(Question::id));
        this.allQuestions = List.copyOf(questions);

        Set<String> skills = new LinkedHashSet<>();
        for (JsonNode s : skillsRoot.get("microskills")) skills.add(s.get("id").asText());
        this.allowedSkills = Set.copyOf(skills);
    }

    private Path resolvePath(String dir) {
        Path local = Path.of(System.getProperty("user.dir"), dir);
        if (Files.exists(local)) return local;
        Path parent = Path.of(System.getProperty("user.dir"), "..", dir).normalize();
        if (Files.exists(parent)) return parent;
        throw new IllegalStateException("Missing required directory: " + dir);
    }

    public List<Question> getAllQuestions() { return allQuestions; }
    public Set<String> getAllowedSkills() { return allowedSkills; }

    public String readPrompt(String name) throws IOException {
        Path promptsDir = resolvePath("prompts");
        return Files.readString(promptsDir.resolve(name));
    }
}

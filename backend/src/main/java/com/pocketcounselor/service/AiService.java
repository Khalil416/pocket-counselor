package com.pocketcounselor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.config.AiConfig;
import com.pocketcounselor.config.DataLoader;
import com.pocketcounselor.dto.ResultsResponse;
import com.pocketcounselor.dto.ScoringResponse;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final Set<Integer> FIBONACCI_POINTS = Set.of(1, 2, 3, 5, 8, 13);
    private static final Set<String> VALID_RESPONSE_TYPES = Set.of("valid", "invalid", "skipped");
    private static final Set<String> VALID_PROFILE_QUALITIES = Set.of("Basic", "Good", "Very Detailed", "Maximum");
    private static final Set<String> VALID_CATEGORY_LABELS = Set.of("Weak", "Basic", "Good", "Strong", "Exceptional");

    private static final Pattern FENCED_JSON = Pattern.compile(
            "```(?:json)?\\s*\\n?(.*?)\\n?\\s*```", Pattern.DOTALL);

    private final AiConfig aiConfig;
    private final DataLoader dataLoader;
    private final PromptService promptService;
    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(AiConfig aiConfig, DataLoader dataLoader,
                     PromptService promptService, WebClient geminiWebClient) {
        this.aiConfig = aiConfig;
        this.dataLoader = dataLoader;
        this.promptService = promptService;
        this.geminiWebClient = geminiWebClient;
    }

    public boolean isMockMode() {
        return aiConfig.isMockMode();
    }

    public ScoringResponse callScoringPrompt(Question question, String answerText) {
        if (isMockMode()) {
            return buildMockScoring(answerText);
        }
        String promptText = promptService.buildScoringPrompt(question, answerText);
        String rawJson = callGemini(promptText, 0.2);
        return parseJson(rawJson, ScoringResponse.class);
    }

    public ResultsResponse callResultsPrompt(Session session) {
        if (isMockMode()) {
            return buildMockResults(session);
        }
        String promptText = promptService.buildResultsPrompt(session);
        String rawJson = callGemini(promptText, 0.7);
        return parseJson(rawJson, ResultsResponse.class);
    }

    // ----- Gemini HTTP call -----

    private String callGemini(String promptText, double temperature) {
        if (!aiConfig.isApiKeyLoaded()) {
            throw new RuntimeException("Gemini API key is not configured. "
                    + "Set gemini.api.key in application.properties or switch to ai.mode=mock.");
        }

        String model = aiConfig.getModel();
        String url = "/v1beta/models/" + model + ":generateContent?key=" + aiConfig.getApiKey();

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", promptText)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "responseMimeType", "application/json"
                )
        );

        log.info("[AI] Gemini request started (model={})", model);

        String responseBody = geminiWebClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .block();

        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("Empty response from Gemini API");
        }

        log.debug("[AI] Gemini raw response length={}", responseBody.length());

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check for API-level error
            if (root.has("error")) {
                int httpCode = root.path("error").path("code").asInt(0);
                String errorMsg = root.path("error").path("message").asText("Unknown Gemini error");
                log.error("[AI] Gemini API error (code={}): {}", httpCode, errorMsg);
                if (httpCode == 429) {
                    log.warn("[AI] Rate limited by Gemini. Waiting 5 seconds before retry...");
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
                throw new RuntimeException("Gemini API error (" + httpCode + "): " + errorMsg);
            }

            String text = root
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText(null);

            if (text == null || text.isBlank()) {
                throw new RuntimeException("No text content in Gemini response");
            }

            log.info("[AI] Gemini request succeeded");

            // Throttle: brief pause after each call to stay under Gemini free-tier rate limit
            try { Thread.sleep(1500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            return text;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse Gemini response envelope", e);
        }
    }

    // ----- JSON extraction & parsing -----

    /**
     * Extract clean JSON from LLM output (handles markdown fences, whitespace)
     * then deserialize into the target type.
     */
    private <T> T parseJson(String raw, Class<T> type) {
        String cleaned = extractJson(raw);
        try {
            return objectMapper.readValue(cleaned, type);
        } catch (JsonProcessingException e) {
            log.error("[AI] Failed to parse JSON into {}: {}", type.getSimpleName(), e.getMessage());
            throw new RuntimeException("AI response JSON parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Strip markdown code fences and whitespace from LLM text output.
     */
    static String extractJson(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();

        // Try to extract from ```json ... ``` or ``` ... ```
        Matcher m = FENCED_JSON.matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }

        return trimmed;
    }

    public boolean validateScoringResponse(ScoringResponse response) {
        if (response == null) { log.warn("[VALIDATE] fail: response is null"); return false; }
        if (!VALID_RESPONSE_TYPES.contains(response.getResponseType())) { log.warn("[VALIDATE] fail: invalid responseType '{}'", response.getResponseType()); return false; }
        if (response.getSkillsDetected() == null) { log.warn("[VALIDATE] fail: skillsDetected is null"); return false; }

        Set<String> allowedSkillIds = dataLoader.getAllowedSkillIds();
        java.util.Set<String> seenSkillIds = new java.util.HashSet<>();
        int computedTotal = 0;

        for (ScoringResponse.SkillDetection sd : response.getSkillsDetected()) {
            if (sd == null) { log.warn("[VALIDATE] fail: null skill entry"); return false; }
            if (seenSkillIds.contains(sd.getSkillId())) { log.warn("[VALIDATE] fail: duplicate skillId '{}'", sd.getSkillId()); return false; }
            seenSkillIds.add(sd.getSkillId());
            if (!allowedSkillIds.contains(sd.getSkillId())) { log.warn("[VALIDATE] fail: unknown skillId '{}'", sd.getSkillId()); return false; }
            if (!FIBONACCI_POINTS.contains(sd.getPoints())) { log.warn("[VALIDATE] fail: non-fibonacci points {} for skill '{}'", sd.getPoints(), sd.getSkillId()); return false; }
            computedTotal += sd.getPoints();
        }

        if (response.getTotalPoints() != computedTotal) { log.warn("[VALIDATE] fail: totalPoints {} != computedTotal {}", response.getTotalPoints(), computedTotal); return false; }

        if ("invalid".equals(response.getResponseType()) || "skipped".equals(response.getResponseType())) {
            if (response.getTotalPoints() != 0 || !response.getSkillsDetected().isEmpty()) { log.warn("[VALIDATE] fail: invalid/skipped but has points or skills"); return false; }
        }

        return true;
    }

    public boolean validateResultsResponse(ResultsResponse response) {
        if (response == null) return false;
        if (!VALID_PROFILE_QUALITIES.contains(response.getProfileQuality())) return false;
        if (response.getOverallSummary() == null) return false;
        if (response.getCategories() == null || response.getStrongestAreas() == null || response.getGrowthAreas() == null) return false;

        for (ResultsResponse.Category cat : response.getCategories()) {
            if (cat == null) return false;
            if (cat.getName() == null || cat.getExplanation() == null) return false;
            if (cat.getScore() < 0 || cat.getScore() > 100) return false;
            if (!VALID_CATEGORY_LABELS.contains(cat.getLabel())) return false;
        }

        for (ResultsResponse.SkillArea area : response.getStrongestAreas()) {
            if (area == null || area.getSkillName() == null || area.getReason() == null) return false;
        }
        for (ResultsResponse.SkillArea area : response.getGrowthAreas()) {
            if (area == null || area.getSkillName() == null || area.getReason() == null) return false;
        }

        return true;
    }

    // ----- Mock implementations -----

    private ScoringResponse buildMockScoring(String answerText) {
        String trimmed = answerText.trim().toLowerCase();

        if (trimmed.contains("[force_ai_fail]")) {
            throw new RuntimeException("forced-failure");
        }

        ScoringResponse response = new ScoringResponse();

        if (trimmed.contains("[force_invalid_schema]")) {
            response.setResponseType("unknown");
            response.setTotalPoints(999);
            response.setSkillsDetected(List.of());
            return response;
        }

        if (trimmed.contains("[force_invalid]")) {
            response.setResponseType("invalid");
            response.setTotalPoints(0);
            response.setSkillsDetected(List.of());
            return response;
        }

        if (trimmed.contains("[force_high]")) {
            ScoringResponse.SkillDetection sd1 = new ScoringResponse.SkillDetection();
            sd1.setSkillId("empathy");
            sd1.setPoints(13);
            ScoringResponse.SkillDetection sd2 = new ScoringResponse.SkillDetection();
            sd2.setSkillId("planning");
            sd2.setPoints(13);
            response.setResponseType("valid");
            response.setTotalPoints(26);
            response.setSkillsDetected(List.of(sd1, sd2));
            return response;
        }

        // Pick skills deterministically based on answer content hash
        List<String> allSkillIds = dataLoader.getMicroskills().stream()
                .map(com.pocketcounselor.model.Microskill::getId)
                .toList();
        int hash = Math.abs(answerText.hashCode());
        int[] fibPoints = {1, 2, 3, 5, 8, 13};

        String skill1 = allSkillIds.get(hash % allSkillIds.size());
        String skill2 = allSkillIds.get((hash / allSkillIds.size()) % allSkillIds.size());
        // Ensure two distinct skills
        if (skill2.equals(skill1)) {
            skill2 = allSkillIds.get((allSkillIds.indexOf(skill1) + 1) % allSkillIds.size());
        }
        int pts1 = fibPoints[hash % fibPoints.length];
        int pts2 = fibPoints[(hash / fibPoints.length) % fibPoints.length];

        ScoringResponse.SkillDetection sd1 = new ScoringResponse.SkillDetection();
        sd1.setSkillId(skill1);
        sd1.setPoints(pts1);
        ScoringResponse.SkillDetection sd2 = new ScoringResponse.SkillDetection();
        sd2.setSkillId(skill2);
        sd2.setPoints(pts2);
        response.setResponseType("valid");
        response.setTotalPoints(pts1 + pts2);
        response.setSkillsDetected(List.of(sd1, sd2));
        return response;
    }

    private ResultsResponse buildMockResults(Session session) {
        ResultsResponse r = new ResultsResponse();
        r.setProfileQuality("Basic");
        r.setDataQualityNote("[MOCK MODE] This is simulated demo data. The AI backend is running in mock mode — no real analysis was performed.");
        r.setOverallSummary("[MOCK DATA] This profile is generated from placeholder values because the system is running in mock/demo mode. "
                + "These scores do not reflect your actual answers. Switch to real AI mode for genuine analysis.");
        r.setCategories(List.of(
                buildCategory("Demo Category A (Mock)", 10, "Weak",
                        "[Mock] This is a placeholder category with a fake 10% score."),
                buildCategory("Demo Category B (Mock)", 20, "Weak",
                        "[Mock] This is a placeholder category with a fake 20% score."),
                buildCategory("Demo Category C (Mock)", 30, "Basic",
                        "[Mock] This is a placeholder category with a fake 30% score."),
                buildCategory("Demo Category D (Mock)", 40, "Basic",
                        "[Mock] This is a placeholder category with a fake 40% score.")
        ));
        r.setStrongestAreas(List.of(
                buildSkillArea("Mock Skill Alpha",
                        "[Mock] This is placeholder data. No real analysis was performed."),
                buildSkillArea("Mock Skill Beta",
                        "[Mock] This is placeholder data. No real analysis was performed.")
        ));
        r.setGrowthAreas(List.of(
                buildSkillArea("Mock Growth Area 1",
                        "[Mock] This is placeholder data. No real analysis was performed."),
                buildSkillArea("Mock Growth Area 2",
                        "[Mock] This is placeholder data. No real analysis was performed.")
        ));
        return r;
    }

    private static ResultsResponse.Category buildCategory(String name, int score, String label, String explanation) {
        ResultsResponse.Category cat = new ResultsResponse.Category();
        cat.setName(name);
        cat.setScore(score);
        cat.setLabel(label);
        cat.setExplanation(explanation);
        return cat;
    }

    private static ResultsResponse.SkillArea buildSkillArea(String skillName, String reason) {
        ResultsResponse.SkillArea area = new ResultsResponse.SkillArea();
        area.setSkillName(skillName);
        area.setReason(reason);
        return area;
    }
}

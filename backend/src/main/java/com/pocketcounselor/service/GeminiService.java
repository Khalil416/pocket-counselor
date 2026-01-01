package com.pocketcounselor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.dto.AnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Service class for interacting with Google Gemini API
 * 
 * This class handles:
 * 1. Reading the API key from application.properties
 * 2. Building the prompt for Gemini
 * 3. Sending the request to Gemini API
 * 4. Parsing the response and formatting it for our frontend
 */
@Service
public class GeminiService {

    // Inject the API key from application.properties
    // The @Value annotation reads from the properties file
    @Value("${gemini.api.key}")
    private String apiKey;

    // Inject the model name from application.properties
    @Value("${gemini.model}")
    private String modelName;

    // Gemini API base URL (using v1 API)
    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1";

    // WebClient for making HTTP requests (Spring WebFlux)
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Analyze user answers and get career recommendations
     * 
     * @param answers Map of question IDs to answer values
     * @return AnalysisResponse with career recommendations
     */
    public AnalysisResponse analyzeAnswers(Map<String, String> answers) {
        try {
            // Build the prompt for Gemini
            String prompt = buildPrompt(answers);

            // Call Gemini API
            String geminiResponse = callGeminiAPI(prompt);

            // Parse and format the response
            return parseGeminiResponse(geminiResponse);

        } catch (Exception e) {
            // If something goes wrong, return a default response
            System.err.println("Error calling Gemini API: " + e.getMessage());
            return getDefaultResponse();
        }
    }

    /**
     * Build a prompt for Gemini based on user answers
     */
    private String buildPrompt(Map<String, String> answers) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a career counselor. Based on the following answers to a career assessment questionnaire, ");
        prompt.append("provide career recommendations. This is NOT medical or therapy advice.\n\n");
        prompt.append("User Answers:\n");
        
        // Add all answers to the prompt
        answers.forEach((questionId, answer) -> {
            prompt.append("- ").append(questionId).append(": ").append(answer).append("\n");
        });
        
        prompt.append("\nPlease provide:\n");
        prompt.append("1. Top 3 career paths that match these answers\n");
        prompt.append("2. A brief explanation (2-3 sentences) for each career path\n");
        prompt.append("3. 3 actionable next steps the user can take\n\n");
        prompt.append("Format your response as JSON with this structure:\n");
        prompt.append("{\n");
        prompt.append("  \"careers\": [\n");
        prompt.append("    {\"title\": \"Career Name\", \"description\": \"Brief explanation\"},\n");
        prompt.append("    ...\n");
        prompt.append("  ],\n");
        prompt.append("  \"nextSteps\": [\"Step 1\", \"Step 2\", \"Step 3\"]\n");
        prompt.append("}\n");
        prompt.append("Keep responses concise and practical.");
        
        return prompt.toString();
    }

    /**
     * Call the Gemini API
     */
    private String callGeminiAPI(String prompt) {
        // Build the request body for Gemini API
        Map<String, Object> requestBody = new HashMap<>();
        
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(part);
        
        content.put("parts", parts);
        
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(content);
        
        requestBody.put("contents", contents);

        // Make the API call
        // Build the URL dynamically using the model name from application.properties
        // Format: https://generativelanguage.googleapis.com/v1/models/{model}:generateContent?key={apiKey}
        String url = GEMINI_API_BASE + "/models/" + modelName + ":generateContent?key=" + apiKey;

        String response = webClient.post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // Wait for the response (blocking call)

        return response;
    }

    /**
     * Parse Gemini's response and convert it to our AnalysisResponse format
     */
    private AnalysisResponse parseGeminiResponse(String geminiResponse) {
        try {
            // Parse the JSON response from Gemini
            JsonNode rootNode = objectMapper.readTree(geminiResponse);
            
            // Extract the text content from Gemini's response
            String textContent = rootNode
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            // Try to extract JSON from the text response
            // Gemini might wrap JSON in markdown code blocks
            String jsonText = textContent;
            if (textContent.contains("```json")) {
                int start = textContent.indexOf("```json") + 7;
                int end = textContent.indexOf("```", start);
                jsonText = textContent.substring(start, end).trim();
            } else if (textContent.contains("```")) {
                int start = textContent.indexOf("```") + 3;
                int end = textContent.indexOf("```", start);
                jsonText = textContent.substring(start, end).trim();
            }

            // Parse the JSON
            JsonNode responseNode = objectMapper.readTree(jsonText);

            // Build our response object
            AnalysisResponse response = new AnalysisResponse();
            List<AnalysisResponse.Career> careers = new ArrayList<>();
            List<String> nextSteps = new ArrayList<>();

            // Extract careers
            JsonNode careersNode = responseNode.path("careers");
            if (careersNode.isArray()) {
                for (JsonNode careerNode : careersNode) {
                    String title = careerNode.path("title").asText();
                    String description = careerNode.path("description").asText();
                    careers.add(new AnalysisResponse.Career(title, description));
                }
            }

            // Extract next steps
            JsonNode nextStepsNode = responseNode.path("nextSteps");
            if (nextStepsNode.isArray()) {
                for (JsonNode stepNode : nextStepsNode) {
                    nextSteps.add(stepNode.asText());
                }
            }

            response.setCareers(careers);
            response.setNextSteps(nextSteps);

            return response;

        } catch (Exception e) {
            System.err.println("Error parsing Gemini response: " + e.getMessage());
            // Return default response if parsing fails
            return getDefaultResponse();
        }
    }

    /**
     * Return a default response if API call fails
     */
    private AnalysisResponse getDefaultResponse() {
        AnalysisResponse response = new AnalysisResponse();
        
        List<AnalysisResponse.Career> careers = new ArrayList<>();
        careers.add(new AnalysisResponse.Career(
            "Software Developer",
            "Build applications and solve technical problems. Great for analytical thinkers who enjoy creating solutions."
        ));
        careers.add(new AnalysisResponse.Career(
            "Project Manager",
            "Lead teams and coordinate projects. Ideal for organized individuals who enjoy collaboration."
        ));
        careers.add(new AnalysisResponse.Career(
            "Designer",
            "Create visual and user experiences. Perfect for creative minds who want to make things beautiful."
        ));
        
        response.setCareers(careers);
        response.setNextSteps(Arrays.asList(
            "Research each career path online",
            "Talk to professionals in these fields",
            "Take online courses to explore your interests"
        ));
        
        return response;
    }
}

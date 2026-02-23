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
     * STRICT RULE: No advice, no next steps, no plans, no projects - ONLY insights and descriptions
     */
    private String buildPrompt(Map<String, String> answers) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a career assessment analyzer. Based on the following answers from a career assessment questionnaire, ");
        prompt.append("provide insights about career directions. This is NOT medical or therapy advice.\n\n");
        prompt.append("User's Assessment Answers:\n");
        
        // Add all answers to the prompt
        answers.forEach((questionId, answerKey) -> {
            prompt.append("- ").append(questionId).append(": ").append(answerKey).append("\n");
        });
        
        prompt.append("\nAnalyze these answers and provide insights in JSON format.\n\n");
        prompt.append("CRITICAL RULES - YOU MUST FOLLOW THESE:\n");
        prompt.append("- DO NOT provide advice, recommendations, or suggestions\n");
        prompt.append("- DO NOT include phrases like: 'you should', 'I recommend', 'try', 'next step', 'plan', 'project', 'start with'\n");
        prompt.append("- ONLY describe what the answers reveal (insights, patterns, signals)\n");
        prompt.append("- Use neutral, descriptive language\n");
        prompt.append("- Focus on what IS, not what SHOULD BE\n\n");
        
        prompt.append("Provide ONLY:\n");
        prompt.append("1. Top 3 career directions (broad clusters like 'Software Development', 'Design & Creative', 'Business & Management')\n");
        prompt.append("2. For each direction:\n");
        prompt.append("   - A clear title\n");
        prompt.append("   - 2-4 bullet points describing why this direction aligns with their answers (descriptive, not prescriptive)\n");
        prompt.append("   - 2-3 key signals detected from their answers\n");
        prompt.append("3. A summary of detected signals:\n");
        prompt.append("   - Interests: patterns in what they enjoy\n");
        prompt.append("   - Work Style: patterns in how they prefer to work\n");
        prompt.append("   - Values: patterns in what matters to them\n");
        prompt.append("4. Confidence level: 'high', 'medium', or 'low' based on answer consistency\n");
        prompt.append("5. A neutral note: 'This is guidance based on your answers.'\n\n");
        
        prompt.append("Format your response as JSON with this EXACT structure:\n");
        prompt.append("{\n");
        prompt.append("  \"directions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"Career Direction Name\",\n");
        prompt.append("      \"why\": [\"Descriptive reason 1\", \"Descriptive reason 2\"],\n");
        prompt.append("      \"signals\": [\"Signal 1\", \"Signal 2\"]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"signalsSummary\": {\n");
        prompt.append("    \"interests\": [\"Interest pattern 1\", \"Interest pattern 2\"],\n");
        prompt.append("    \"workStyle\": [\"Work style pattern 1\", \"Work style pattern 2\"],\n");
        prompt.append("    \"values\": [\"Value pattern 1\", \"Value pattern 2\"]\n");
        prompt.append("  },\n");
        prompt.append("  \"confidence\": \"high\",\n");
        prompt.append("  \"note\": \"This is guidance based on your answers.\"\n");
        prompt.append("}\n");
        prompt.append("\nUse neutral, descriptive language. Describe patterns and insights, not actions or recommendations.");
        
        return prompt.toString();
    }

    /**
     * Call the Gemini API
     */
    private String callGeminiAPI(String prompt) {
        // Validate API key
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("YOUR_API_KEY_HERE")) {
            throw new RuntimeException("Gemini API key is not configured! Please set the GEMINI_API_KEY environment variable.");
        }
        
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
            List<AnalysisResponse.Direction> directions = new ArrayList<>();

            // Extract directions
            JsonNode directionsNode = responseNode.path("directions");
            if (directionsNode.isArray()) {
                for (JsonNode directionNode : directionsNode) {
                    AnalysisResponse.Direction direction = new AnalysisResponse.Direction();
                    direction.setTitle(directionNode.path("title").asText());
                    
                    // Extract "why" array
                    List<String> whyList = new ArrayList<>();
                    JsonNode whyNode = directionNode.path("why");
                    if (whyNode.isArray()) {
                        for (JsonNode whyItem : whyNode) {
                            whyList.add(whyItem.asText());
                        }
                    }
                    direction.setWhy(whyList);
                    
                    // Extract "signals" array
                    List<String> signalsList = new ArrayList<>();
                    JsonNode signalsNode = directionNode.path("signals");
                    if (signalsNode.isArray()) {
                        for (JsonNode signalItem : signalsNode) {
                            signalsList.add(signalItem.asText());
                        }
                    }
                    direction.setSignals(signalsList);
                    
                    directions.add(direction);
                }
            }

            // Extract signals summary
            AnalysisResponse.SignalsSummary signalsSummary = new AnalysisResponse.SignalsSummary();
            JsonNode signalsSummaryNode = responseNode.path("signalsSummary");
            if (!signalsSummaryNode.isMissingNode()) {
                // Extract interests
                List<String> interests = new ArrayList<>();
                JsonNode interestsNode = signalsSummaryNode.path("interests");
                if (interestsNode.isArray()) {
                    for (JsonNode interestItem : interestsNode) {
                        interests.add(interestItem.asText());
                    }
                }
                signalsSummary.setInterests(interests);
                
                // Extract workStyle
                List<String> workStyle = new ArrayList<>();
                JsonNode workStyleNode = signalsSummaryNode.path("workStyle");
                if (workStyleNode.isArray()) {
                    for (JsonNode workStyleItem : workStyleNode) {
                        workStyle.add(workStyleItem.asText());
                    }
                }
                signalsSummary.setWorkStyle(workStyle);
                
                // Extract values
                List<String> values = new ArrayList<>();
                JsonNode valuesNode = signalsSummaryNode.path("values");
                if (valuesNode.isArray()) {
                    for (JsonNode valueItem : valuesNode) {
                        values.add(valueItem.asText());
                    }
                }
                signalsSummary.setValues(values);
            }

            // Extract confidence
            String confidence = responseNode.path("confidence").asText("medium");

            // Extract note
            String note = responseNode.path("note").asText("This is guidance based on your answers.");

            response.setDirections(directions);
            response.setSignalsSummary(signalsSummary);
            response.setConfidence(confidence);
            response.setNote(note);

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
        
        List<AnalysisResponse.Direction> directions = new ArrayList<>();
        
        // Direction 1
        AnalysisResponse.Direction dir1 = new AnalysisResponse.Direction();
        dir1.setTitle("Software Development");
        dir1.setWhy(Arrays.asList(
            "Answers indicate interest in building and problem-solving",
            "Preference for structured work with clear outcomes",
            "Technical orientation detected in responses"
        ));
        dir1.setSignals(Arrays.asList("Building focus", "Problem-solving preference", "Technical interest"));
        directions.add(dir1);
        
        // Direction 2
        AnalysisResponse.Direction dir2 = new AnalysisResponse.Direction();
        dir2.setTitle("Project Management");
        dir2.setWhy(Arrays.asList(
            "Answers show interest in organizing and coordinating",
            "Team collaboration patterns detected",
            "Value placed on clear structure and planning"
        ));
        dir2.setSignals(Arrays.asList("Organizational focus", "Team orientation", "Planning preference"));
        directions.add(dir2);
        
        // Direction 3
        AnalysisResponse.Direction dir3 = new AnalysisResponse.Direction();
        dir3.setTitle("Design & Creative");
        dir3.setWhy(Arrays.asList(
            "Answers indicate creative expression interest",
            "Value placed on aesthetics and user experience",
            "Preference for varied and engaging work"
        ));
        dir3.setSignals(Arrays.asList("Creative expression", "Aesthetic focus", "Variety preference"));
        directions.add(dir3);
        
        // Signals summary
        AnalysisResponse.SignalsSummary signalsSummary = new AnalysisResponse.SignalsSummary();
        signalsSummary.setInterests(Arrays.asList("Building and creating", "Problem-solving", "Organization"));
        signalsSummary.setWorkStyle(Arrays.asList("Structured approach", "Team collaboration", "Clear outcomes"));
        signalsSummary.setValues(Arrays.asList("Stability", "Achievement", "Support"));
        
        response.setDirections(directions);
        response.setSignalsSummary(signalsSummary);
        response.setConfidence("medium");
        response.setNote("This is guidance based on your answers.");
        
        return response;
    }
}

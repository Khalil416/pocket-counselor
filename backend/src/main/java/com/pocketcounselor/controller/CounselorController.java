package com.pocketcounselor.controller;

import com.pocketcounselor.dto.AnalysisRequest;
import com.pocketcounselor.dto.AnalysisResponse;
import com.pocketcounselor.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling HTTP requests
 * 
 * This class defines the API endpoints that the frontend can call.
 * 
 * @RestController = This class handles HTTP requests
 * @CrossOrigin = Allows frontend (running on different port) to call this API
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow requests from any origin (for development)
public class CounselorController {

    // Inject the GeminiService
    // Spring will automatically create and inject this service
    @Autowired
    private GeminiService geminiService;

    /**
     * POST endpoint to analyze user answers
     * 
     * This endpoint:
     * 1. Receives answers from frontend
     * 2. Calls GeminiService to get AI recommendations
     * 3. Returns the results to frontend
     * 
     * URL: http://localhost:8080/api/analyze
     * Method: POST
     * Body: { "answers": { "q1": "creative", "q2": "team", ... } }
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@RequestBody AnalysisRequest request) {
        try {
            // Get answers from the request
            var answers = request.getAnswers();
            
            // Validate that we have answers
            if (answers == null || answers.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Call the Gemini service to analyze answers
            AnalysisResponse response = geminiService.analyzeAnswers(answers);

            // Return the response
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // If something goes wrong, return an error
            System.err.println("Error in analyze endpoint: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET endpoint to check if the API is running
     * 
     * This is useful for testing if the backend is up
     * URL: http://localhost:8080/api/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Pocket Counselor API is running! ✅");
    }
}


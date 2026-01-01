package com.pocketcounselor.dto;

import java.util.Map;

/**
 * Data Transfer Object (DTO) for receiving answers from frontend
 * 
 * This class represents the JSON structure sent from the frontend:
 * {
 *   "answers": {
 *     "q1": "creative",
 *     "q2": "team",
 *     ...
 *   }
 * }
 */
public class AnalysisRequest {
    
    // Map to store question IDs and their answers
    private Map<String, String> answers;

    // Getter method
    public Map<String, String> getAnswers() {
        return answers;
    }

    // Setter method
    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}


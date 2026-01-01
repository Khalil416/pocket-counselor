package com.pocketcounselor.dto;

import java.util.List;

/**
 * Data Transfer Object (DTO) for sending results back to frontend
 * 
 * This class represents the JSON structure sent to the frontend:
 * {
 *   "careers": [
 *     { "title": "...", "description": "..." },
 *     ...
 *   ],
 *   "nextSteps": ["step1", "step2", "step3"]
 * }
 */
public class AnalysisResponse {
    
    // List of recommended career paths
    private List<Career> careers;
    
    // List of next steps for the user
    private List<String> nextSteps;

    // Getter and Setter methods
    public List<Career> getCareers() {
        return careers;
    }

    public void setCareers(List<Career> careers) {
        this.careers = careers;
    }

    public List<String> getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(List<String> nextSteps) {
        this.nextSteps = nextSteps;
    }

    /**
     * Inner class to represent a single career recommendation
     */
    public static class Career {
        private String title;
        private String description;

        public Career() {}

        public Career(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}


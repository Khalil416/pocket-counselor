package com.pocketcounselor.dto;

import java.util.List;

/**
 * Data Transfer Object (DTO) for sending results back to frontend
 * 
 * This class represents the JSON structure sent to the frontend:
 * {
 *   "directions": [
 *     {
 *       "title": "...",
 *       "why": ["...", "..."],
 *       "signals": ["...", "..."]
 *     }
 *   ],
 *   "signalsSummary": {
 *     "interests": ["...","..."],
 *     "workStyle": ["...","..."],
 *     "values": ["...","..."]
 *   },
 *   "confidence": "high",
 *   "note": "This is guidance based on your answers."
 * }
 */
public class AnalysisResponse {
    
    // List of career directions (top 3)
    private List<Direction> directions;
    
    // Summary of detected signals
    private SignalsSummary signalsSummary;
    
    // Confidence level: "high", "medium", or "low"
    private String confidence;
    
    // Note/disclaimer
    private String note;

    // Getter and Setter methods
    public List<Direction> getDirections() {
        return directions;
    }

    public void setDirections(List<Direction> directions) {
        this.directions = directions;
    }

    public SignalsSummary getSignalsSummary() {
        return signalsSummary;
    }

    public void setSignalsSummary(SignalsSummary signalsSummary) {
        this.signalsSummary = signalsSummary;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /**
     * Inner class to represent a single career direction
     */
    public static class Direction {
        private String title;
        private List<String> why;
        private List<String> signals;

        public Direction() {}

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<String> getWhy() {
            return why;
        }

        public void setWhy(List<String> why) {
            this.why = why;
        }

        public List<String> getSignals() {
            return signals;
        }

        public void setSignals(List<String> signals) {
            this.signals = signals;
        }
    }

    /**
     * Inner class to represent signals summary
     */
    public static class SignalsSummary {
        private List<String> interests;
        private List<String> workStyle;
        private List<String> values;

        public SignalsSummary() {}

        public List<String> getInterests() {
            return interests;
        }

        public void setInterests(List<String> interests) {
            this.interests = interests;
        }

        public List<String> getWorkStyle() {
            return workStyle;
        }

        public void setWorkStyle(List<String> workStyle) {
            this.workStyle = workStyle;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}

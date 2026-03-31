package com.pocketcounselor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ScoringResponse {

    public static class SkillDetection {
        @JsonProperty("skill_id")
        private String skillId;
        private int points;

        public SkillDetection() {}

        public String getSkillId() { return skillId; }
        public void setSkillId(String skillId) { this.skillId = skillId; }

        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
    }

    @JsonProperty("response_type")
    private String responseType;

    @JsonProperty("total_points")
    private int totalPoints;

    @JsonProperty("skills_detected")
    private List<SkillDetection> skillsDetected;

    public ScoringResponse() {}

    public String getResponseType() { return responseType; }
    public void setResponseType(String responseType) { this.responseType = responseType; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }

    public List<SkillDetection> getSkillsDetected() { return skillsDetected; }
    public void setSkillsDetected(List<SkillDetection> skillsDetected) { this.skillsDetected = skillsDetected; }
}

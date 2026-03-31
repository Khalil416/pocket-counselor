package com.pocketcounselor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ResultsResponse {

    public static class Category {
        private String name;
        private int score;
        private String label;
        private String explanation;

        public Category() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }

    public static class SkillArea {
        @JsonProperty("skill_name")
        private String skillName;
        private String reason;

        public SkillArea() {}

        public String getSkillName() { return skillName; }
        public void setSkillName(String skillName) { this.skillName = skillName; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @JsonProperty("profile_quality")
    private String profileQuality;

    @JsonProperty("overall_summary")
    private String overallSummary;

    @JsonProperty("data_quality_note")
    private String dataQualityNote;

    private List<Category> categories;

    @JsonProperty("strongest_areas")
    private List<SkillArea> strongestAreas;

    @JsonProperty("growth_areas")
    private List<SkillArea> growthAreas;

    public ResultsResponse() {}

    public String getProfileQuality() { return profileQuality; }
    public void setProfileQuality(String profileQuality) { this.profileQuality = profileQuality; }

    public String getOverallSummary() { return overallSummary; }
    public void setOverallSummary(String overallSummary) { this.overallSummary = overallSummary; }

    public String getDataQualityNote() { return dataQualityNote; }
    public void setDataQualityNote(String dataQualityNote) { this.dataQualityNote = dataQualityNote; }

    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }

    public List<SkillArea> getStrongestAreas() { return strongestAreas; }
    public void setStrongestAreas(List<SkillArea> strongestAreas) { this.strongestAreas = strongestAreas; }

    public List<SkillArea> getGrowthAreas() { return growthAreas; }
    public void setGrowthAreas(List<SkillArea> growthAreas) { this.growthAreas = growthAreas; }
}

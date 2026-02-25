package com.pocketcounselor.model;

import java.util.List;

public record ScoringResult(String response_type, int total_points, List<SkillPoint> skills_detected) {
    public record SkillPoint(String skill_id, int points) {}
}

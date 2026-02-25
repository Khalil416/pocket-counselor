package com.pocketcounselor.model;

import java.util.List;

public record ResultsPayload(
        String profile_quality,
        String overall_summary,
        List<Category> categories,
        List<SkillReason> strongest_areas,
        List<SkillReason> growth_areas
) {
    public record Category(String name, int score, String label, String explanation) {}
    public record SkillReason(String skill_name, String reason) {}
}

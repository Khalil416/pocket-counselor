package com.pocketcounselor.validation;

import com.pocketcounselor.model.ResultsPayload;
import com.pocketcounselor.model.ScoringResult;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AiValidationService {
    private static final Set<Integer> FIB = Set.of(1,2,3,5,8,13);
    private static final Set<String> SCORE_TYPES = Set.of("valid","invalid","skipped");
    private static final Set<String> PROFILE = Set.of("Basic","Good","Very Detailed","Maximum");
    private static final Set<String> LABEL = Set.of("Weak","Basic","Good","Strong","Exceptional");

    public boolean validScoring(ScoringResult s, Set<String> allowedSkills) {
        if (s == null || !SCORE_TYPES.contains(s.response_type()) || s.skills_detected() == null) return false;
        int sum = 0;
        for (var sp : s.skills_detected()) {
            if (sp == null || sp.skill_id() == null || !allowedSkills.contains(sp.skill_id()) || "INVALID".equals(sp.skill_id())) return false;
            if (!FIB.contains(sp.points())) return false;
            sum += sp.points();
        }
        return sum == s.total_points();
    }

    public boolean validResults(ResultsPayload r) {
        if (r == null || !PROFILE.contains(r.profile_quality()) || r.overall_summary() == null) return false;
        if (r.categories() == null || r.strongest_areas() == null || r.growth_areas() == null) return false;
        return r.categories().stream().allMatch(c -> c.name()!=null && LABEL.contains(c.label()) && c.explanation()!=null)
                && r.strongest_areas().stream().allMatch(s -> s.skill_name()!=null && s.reason()!=null)
                && r.growth_areas().stream().allMatch(s -> s.skill_name()!=null && s.reason()!=null);
    }
}

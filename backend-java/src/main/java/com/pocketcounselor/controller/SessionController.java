package com.pocketcounselor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pocketcounselor.dto.ApiDtos;
import com.pocketcounselor.model.Question;
import com.pocketcounselor.model.Session;
import com.pocketcounselor.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = "*")
public class SessionController {
    private final SessionService sessions;

    public SessionController(SessionService sessions) { this.sessions = sessions; }

    @PostMapping("/start")
    public ApiDtos.StartResponse start() {
        Session s = sessions.start();
        Question q = sessions.nextQuestion(s);
        return new ApiDtos.StartResponse(s.sessionId, q, snapshot(s), s.counters.questionsSkipped < 7);
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<?> answer(@PathVariable String sessionId, @RequestBody JsonNode body) {
        Session s = sessions.get(sessionId);
        if (s == null) return ResponseEntity.notFound().build();
        Integer questionId = body.hasNonNull("questionId") ? body.get("questionId").asInt() : null;
        String answerText = body.hasNonNull("answerText") ? body.get("answerText").asText() : (body.hasNonNull("answer") ? body.get("answer").asText() : null);
        if (questionId==null || answerText==null) return ResponseEntity.badRequest().body(Map.of("error", "questionId and answerText required"));
        if (answerText.trim().length() < 10) return ResponseEntity.badRequest().body(Map.of("error", "answer_too_short"));

        Question q = s.queue.stream().filter(x -> x.id()==questionId).findFirst().orElse(null);
        if (q == null) return ResponseEntity.badRequest().body(Map.of("error", "Invalid question ID"));

        sessions.scoreAsync(s, q, answerText.trim());
        Question next = sessions.nextQuestion(s);
        boolean force = s.counters.questionsShown >= 35;
        if (next == null || force) s.finished = true;
        if (force && s.checkpoints.reached == 0) s.hasLowDataWarning = true;

        return ResponseEntity.ok(new ApiDtos.AnswerResponse(next, snapshot(s), s.counters.questionsSkipped < 7, next == null || force, force, force && s.checkpoints.reached==0));
    }

    @PostMapping("/{sessionId}/skip")
    public ResponseEntity<?> skip(@PathVariable String sessionId, @RequestBody ApiDtos.SkipRequest body) {
        Session s = sessions.get(sessionId);
        if (s == null) return ResponseEntity.notFound().build();
        if (body.questionId()==null) return ResponseEntity.badRequest().body(Map.of("error", "questionId is required"));
        if (s.counters.questionsSkipped >= 7) return ResponseEntity.badRequest().body(Map.of("error", "Maximum skips reached"));

        sessions.skip(s, body.questionId());
        Question next = sessions.nextQuestion(s);
        boolean force = s.counters.questionsShown >= 35;
        if (next == null || force) s.finished = true;
        if (force && s.checkpoints.reached == 0) s.hasLowDataWarning = true;
        return ResponseEntity.ok(Map.of("skipped", true, "nextQuestion", next, "session", snapshot(s), "canSkip", s.counters.questionsSkipped < 7, "finished", next == null || force));
    }

    @GetMapping("/{sessionId}/state")
    public ResponseEntity<?> state(@PathVariable String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) return ResponseEntity.notFound().build();
        boolean hit = s.pendingCheckpoint != null;
        Integer cp = s.pendingCheckpoint;
        s.pendingCheckpoint = null;
        return ResponseEntity.ok(new ApiDtos.StateResponse(hit, s.checkpoints.reached, cp, s.hasLowDataWarning, snapshot(s)));
    }

    @PostMapping("/{sessionId}/results")
    public ResponseEntity<?> results(@PathVariable String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) return ResponseEntity.notFound().build();
        var rr = sessions.results(s);
        java.util.Map<String,Object> out = new java.util.LinkedHashMap<>();
        out.put("profile_quality", rr.payload().profile_quality());
        out.put("overall_summary", rr.payload().overall_summary());
        out.put("categories", rr.payload().categories());
        out.put("strongest_areas", rr.payload().strongest_areas());
        out.put("growth_areas", rr.payload().growth_areas());
        out.put("low_data_quality_warning", rr.lowDataWarning());
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{sessionId}/results")
    public ResponseEntity<?> resultsGet(@PathVariable String sessionId) { return results(sessionId); }

        private Session snapshot(Session s) {
        Session v = new Session(s.sessionId, java.util.List.of(), new java.util.LinkedHashMap<>(s.microSkillScores));
        v.counters.questionsShown = s.counters.questionsShown;
        v.counters.questionsAnswered = s.counters.questionsAnswered;
        v.counters.questionsSkipped = s.counters.questionsSkipped;
        v.counters.invalidAnswers = s.counters.invalidAnswers;
        v.points.total = s.points.total;
        v.checkpoints.reached = s.checkpoints.reached;
        return v;
    }
}

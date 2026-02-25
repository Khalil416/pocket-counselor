package com.pocketcounselor.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Session {
    public final String sessionId;
    public final String startedAt;
    public final Counters counters = new Counters();
    public final Points points = new Points();
    public final Checkpoints checkpoints = new Checkpoints();
    public final Map<String, Integer> microSkillScores;
    public final List<AnswerRecord> answers = Collections.synchronizedList(new ArrayList<>());

    public final List<Question> queue;
    public int currentIndex = 0;
    public volatile boolean finished = false;
    public volatile Integer pendingCheckpoint = null;
    public volatile boolean hasLowDataWarning = false;
    public volatile boolean resultsGenerated = false;
    public volatile ResultsPayload resultsCache = null;
    public final AtomicInteger scoringAttempts = new AtomicInteger(0);

    public Session(String sessionId, List<Question> queue, Map<String, Integer> microSkillScores) {
        this.sessionId = sessionId;
        this.startedAt = Instant.now().toString();
        this.queue = queue;
        this.microSkillScores = microSkillScores;
    }

    public static class Counters {
        public int questionsShown = 0;
        public int questionsAnswered = 0;
        public int questionsSkipped = 0;
        public int invalidAnswers = 0;
    }

    public static class Points { public int total = 0; }
    public static class Checkpoints { public int reached = 0; }
}

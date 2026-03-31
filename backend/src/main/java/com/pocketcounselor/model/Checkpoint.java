package com.pocketcounselor.model;

import java.util.List;

public class Checkpoint {

    private final int level;
    private final int points;
    private final int minQuestions;
    private final String label;

    public static final List<Checkpoint> ALL = List.of(
            new Checkpoint(1, 280, 10, "Basic"),
            new Checkpoint(2, 420, 15, "Good"),
            new Checkpoint(3, 560, 20, "Very Detailed"),
            new Checkpoint(4, 700, 25, "Maximum")
    );

    public Checkpoint(int level, int points, int minQuestions, String label) {
        this.level = level;
        this.points = points;
        this.minQuestions = minQuestions;
        this.label = label;
    }

    public int getLevel() { return level; }
    public int getPoints() { return points; }
    public int getMinQuestions() { return minQuestions; }
    public String getLabel() { return label; }

    public static String getProfileLabel(int checkpointReached) {
        return ALL.stream()
                .filter(cp -> cp.level == checkpointReached)
                .map(Checkpoint::getLabel)
                .findFirst()
                .orElse("Preliminary");
    }
}

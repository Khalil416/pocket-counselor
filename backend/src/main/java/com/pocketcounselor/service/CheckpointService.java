package com.pocketcounselor.service;

import com.pocketcounselor.model.Checkpoint;
import com.pocketcounselor.model.CheckpointResult;
import com.pocketcounselor.model.Session;
import org.springframework.stereotype.Service;

@Service
public class CheckpointService {

    /**
     * Check if a checkpoint has been reached.
     * Only checks every 3 answered questions (skipped do NOT count, invalid DO count).
     * Only checks for the NEXT checkpoint not yet reached.
     */
    public CheckpointResult checkForCheckpoint(Session session) {
        int answered = session.getCounters().getQuestionsAnswered();
        int totalPoints = session.getPoints().getTotal();
        int reached = session.getCheckpoints().getReached();

        if (answered < 3 || answered % 3 != 0) {
            return null;
        }

        Checkpoint nextCp = Checkpoint.ALL.stream()
                .filter(cp -> cp.getLevel() == reached + 1)
                .findFirst()
                .orElse(null);

        if (nextCp == null) {
            return null;
        }

        if (totalPoints >= nextCp.getPoints() && answered >= nextCp.getMinQuestions()) {
            session.getCheckpoints().setReached(nextCp.getLevel());
            boolean resultsReady = answered >= 10
                    && session.getLastScoredAnswerIndex() >= 10;
            return new CheckpointResult(
                    nextCp.getLevel(),
                    nextCp.getLabel(),
                    answered,
                    totalPoints,
                    nextCp.getLevel() == 4,
                    resultsReady
            );
        }

        return null;
    }

    public String getProfileLabel(int checkpointReached) {
        return Checkpoint.getProfileLabel(checkpointReached);
    }
}

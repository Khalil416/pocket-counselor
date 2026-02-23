/**
 * CHECKPOINT MODULE
 * Pure checkpoint checking logic.
 * Check after every 3 answered questions (valid + invalid count, skipped do not count).
 */

const CHECKPOINTS = [
    { level: 1, points: 280, minQuestions: 10, label: 'Basic' },
    { level: 2, points: 420, minQuestions: 15, label: 'Good' },
    { level: 3, points: 560, minQuestions: 20, label: 'Very Detailed' },
    { level: 4, points: 700, minQuestions: 25, label: 'Maximum Depth' }
];

/**
 * Check if a checkpoint has been reached.
 * Only checks every 3 answered questions.
 * Only checks for the NEXT checkpoint not yet reached.
 * @param {Object} session - The session object
 * @returns {Object|null} Checkpoint info or null
 */
function checkForCheckpoint(session) {
    const answered = session.counters.questionsAnswered;
    const totalPoints = session.points.total;
    const reached = session.checkpoints.reached;

    // Only run checkpoint logic after every 3 ANSWERED questions
    // (skipped do NOT count, invalid DO count) — matches spec Section 7.
    if (answered < 3 || answered % 3 !== 0) {
        return null;
    }

    // Find the next checkpoint not yet reached
    const nextCP = CHECKPOINTS.find(cp => cp.level === reached + 1);
    if (!nextCP) return null; // all checkpoints already reached

    // Check if thresholds are met
    if (totalPoints >= nextCP.points && answered >= nextCP.minQuestions) {
        session.checkpoints.reached = nextCP.level;
        return {
            level: nextCP.level,
            label: nextCP.label,
            questionsAnswered: answered,
            totalPoints: totalPoints,
            autoShow: nextCP.level === 4 // CP4 auto-shows results
        };
    }

    return null;
}

/**
 * Get profile quality label based on checkpoint reached
 */
function getProfileLabel(checkpointReached) {
    const cp = CHECKPOINTS.find(c => c.level === checkpointReached);
    if (cp) return cp.label;
    return 'Preliminary';
}

module.exports = {
    checkForCheckpoint,
    getProfileLabel,
    CHECKPOINTS
};

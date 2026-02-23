/**
 * SESSION MANAGEMENT
 * Handles creation, storage, and management of quiz sessions in memory.
 * Applies AI scoring results to session (Section 4 of spec).
 */

const { v4: uuidv4 } = require('uuid');
const fs = require('fs');
const path = require('path');

// In-memory session store
const sessions = new Map();

// Load micro-skills catalog once at startup
const microskillsPath = path.join(__dirname, '..', 'data', 'microskills.json');
const { microskills } = JSON.parse(fs.readFileSync(microskillsPath, 'utf-8'));

// Load questions once at startup
const questionsPath = path.join(__dirname, '..', 'data', 'questions.json');
const { questions } = JSON.parse(fs.readFileSync(questionsPath, 'utf-8'));

/**
 * Build default microSkillScores with all 77 skills set to 0 + INVALID key
 */
function buildEmptyScores() {
  const scores = {};
  for (const skill of microskills) {
    scores[skill.id] = 0;
  }
  scores['INVALID'] = 0;
  return scores;
}

/**
 * Build the ordered question queue: Tier 1 → 2 → 3
 */
function buildQuestionQueue() {
  const tier1 = questions.filter(q => q.tier === 1).sort((a, b) => a.id - b.id);
  const tier2 = questions.filter(q => q.tier === 2).sort((a, b) => a.id - b.id);
  const tier3 = questions.filter(q => q.tier === 3).sort((a, b) => a.id - b.id);
  return [...tier1, ...tier2, ...tier3];
}

/**
 * Create a new session — exact structure from Section 3 of spec
 */
function createSession() {
  const sessionId = uuidv4();
  const questionQueue = buildQuestionQueue();

  const session = {
    sessionId,
    startedAt: new Date().toISOString(),

    counters: {
      questionsShown: 0,
      questionsAnswered: 0,
      questionsSkipped: 0,
      invalidAnswers: 0
    },

    points: {
      total: 0
    },

    checkpoints: {
      reached: 0
    },

    microSkillScores: buildEmptyScores(),

    answers: [],

    // Internal tracking (not sent to client)
    _questionQueue: questionQueue,
    _currentIndex: 0,
    _skippedQuestionIds: new Set(),
    _finished: false
  };

  sessions.set(sessionId, session);
  return session;
}

/**
 * Get session by ID
 */
function getSession(sessionId) {
  return sessions.get(sessionId) || null;
}

/**
 * Get the next question for this session
 */
function getNextQuestion(session) {
  if (session._finished) return null;
  if (session.counters.questionsShown >= 35) return null;
  if (session._currentIndex >= session._questionQueue.length) return null;

  const currentQ = session._questionQueue[session._currentIndex];
  if (!currentQ) return null;

  session.counters.questionsShown++;
  session._currentIndex++;

  return {
    id: currentQ.id,
    text: currentQ.text,
    questionNumber: session.counters.questionsShown,
    signal_level: currentQ.signal_level,
    expected_points: currentQ.expected_points
  };
}

/**
 * Handle a user-initiated skip (the Skip button)
 */
function skipQuestion(session, questionId) {
  session.counters.questionsSkipped++;
  session._skippedQuestionIds.add(questionId);

  // Check if a backup question exists and should be prioritized
  const backupQ = session._questionQueue.find(
    q => q.backup_for === questionId && !session._skippedQuestionIds.has(q.id)
  );

  if (backupQ) {
    const backupIdx = session._questionQueue.indexOf(backupQ);
    if (backupIdx > session._currentIndex) {
      session._questionQueue.splice(backupIdx, 1);
      session._questionQueue.splice(session._currentIndex, 0, backupQ);
    }
  }

  return session.counters.questionsSkipped >= 7;
}

/**
 * Check if skip is allowed
 */
function canSkip(session) {
  return session.counters.questionsSkipped < 7;
}

/**
 * Mark session as finished
 */
function finishSession(session) {
  session._finished = true;
}

/**
 * Get question data by ID
 */
function getQuestionById(questionId) {
  return questions.find(q => q.id === questionId) || null;
}

/**
 * Apply AI scoring response to session — Section 4 of spec
 * This is the ONLY place the code touches scores.
 */
function applyAiScoring(session, questionData, answerText, aiResponse) {
  if (aiResponse.response_type === 'valid') {
    // Add points for each detected skill
    if (aiResponse.skills_detected && Array.isArray(aiResponse.skills_detected)) {
      for (const detection of aiResponse.skills_detected) {
        if (detection.skill_id in session.microSkillScores && detection.skill_id !== 'INVALID') {
          session.microSkillScores[detection.skill_id] += detection.points;
        }
      }
    }
    session.points.total += (aiResponse.total_points || 0);
    session.counters.questionsAnswered++;

    session.answers.push({
      questionId: questionData.id,
      answerText,
      responseType: 'valid',
      pointsEarned: aiResponse.total_points || 0
    });

  } else if (aiResponse.response_type === 'skipped') {
    // AI determined answer is a skip — do NOT count toward answered
    session.counters.questionsSkipped++;

    session.answers.push({
      questionId: questionData.id,
      answerText,
      responseType: 'skipped',
      pointsEarned: 0
    });

  } else if (aiResponse.response_type === 'invalid') {
    session.microSkillScores['INVALID'] += 1;
    session.counters.invalidAnswers++;
    session.counters.questionsAnswered++; // invalid still counts as answered
    // points.total += 0

    session.answers.push({
      questionId: questionData.id,
      answerText,
      responseType: 'invalid',
      pointsEarned: 0
    });
  }
}

/**
 * Get a sanitized session for client response (strips internal fields)
 */
function getClientSession(session) {
  return {
    sessionId: session.sessionId,
    counters: session.counters,
    points: session.points,
    checkpoints: session.checkpoints
  };
}

module.exports = {
  createSession,
  getSession,
  getNextQuestion,
  skipQuestion,
  canSkip,
  finishSession,
  getQuestionById,
  applyAiScoring,
  getClientSession
};

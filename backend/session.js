const { v4: uuidv4 } = require('uuid');
const fs = require('fs');
const path = require('path');

const sessions = new Map();

const microskillsPath = path.join(__dirname, '..', 'data', 'microskills.json');
const questionsPath = path.join(__dirname, '..', 'data', 'questions.json');
const { microskills } = JSON.parse(fs.readFileSync(microskillsPath, 'utf-8'));
const { questions } = JSON.parse(fs.readFileSync(questionsPath, 'utf-8'));

const ALLOWED_SKILL_IDS = new Set(microskills.map((s) => s.id));
const FIBONACCI_POINTS = new Set([1, 2, 3, 5, 8, 13]);

function buildEmptyScores() {
  const scores = {};
  for (const skill of microskills) scores[skill.id] = 0;
  scores.INVALID = 0;
  return scores;
}

function buildQuestionQueue() {
  const tier1 = questions.filter((q) => q.tier === 1).sort((a, b) => a.id - b.id);
  const tier2 = questions.filter((q) => q.tier === 2).sort((a, b) => a.id - b.id);
  const tier3 = questions.filter((q) => q.tier === 3).sort((a, b) => a.id - b.id);
  return [...tier1, ...tier2, ...tier3];
}

function createSession() {
  const session = {
    sessionId: uuidv4(),
    startedAt: new Date().toISOString(),
    counters: { questionsShown: 0, questionsAnswered: 0, questionsSkipped: 0, invalidAnswers: 0 },
    points: { total: 0 },
    checkpoints: { reached: 0 },
    microSkillScores: buildEmptyScores(),
    answers: [],
    _questionQueue: buildQuestionQueue(),
    _currentIndex: 0,
    _skippedQuestionIds: new Set(),
    _finished: false,
    _pendingCheckpoint: null,
    _pendingWarning: null,
    _hasLowDataWarning: false,
    _resultsCache: null,
    _resultsRequested: false,
    _lastAnswerSubmissionIndex: 0,
    _lastScoredAnswerIndex: 0
  };
  sessions.set(session.sessionId, session);
  return session;
}

function getSession(sessionId) { return sessions.get(sessionId) || null; }

function getNextQuestion(session) {
  if (session._finished || session.counters.questionsShown >= 35 || session._currentIndex >= session._questionQueue.length) {
    return null;
  }
  const currentQ = session._questionQueue[session._currentIndex++];
  session.counters.questionsShown += 1;
  return { id: currentQ.id, text: currentQ.text, questionNumber: session.counters.questionsShown, expected_points: currentQ.expected_points };
}

function skipQuestion(session, questionId) {
  session.counters.questionsSkipped += 1;
  session.answers.push({ questionId, answerText: '', status: 'skipped', responseType: 'skipped', pointsEarned: 0 });
  session._skippedQuestionIds.add(questionId);

  const backupQ = session._questionQueue.find((q) => q.backup_for === questionId && !session._skippedQuestionIds.has(q.id));
  if (backupQ) {
    const idx = session._questionQueue.indexOf(backupQ);
    if (idx > session._currentIndex) {
      session._questionQueue.splice(idx, 1);
      session._questionQueue.splice(session._currentIndex, 0, backupQ);
    }
  }

  return session.counters.questionsSkipped >= 7;
}

function canSkip(session) { return session.counters.questionsSkipped < 7; }
function finishSession(session) { session._finished = true; }
function getQuestionById(questionId) { return questions.find((q) => q.id === questionId) || null; }

function validateScoringResponse(aiResponse) {
  if (!aiResponse || typeof aiResponse !== 'object') return false;
  if (!['valid', 'invalid', 'skipped'].includes(aiResponse.response_type)) return false;
  if (!Number.isInteger(aiResponse.total_points)) return false;
  if (!Array.isArray(aiResponse.skills_detected)) return false;

  const seenSkillIds = new Set();
  let computedTotal = 0;
  for (const s of aiResponse.skills_detected) {
    if (!s || typeof s !== 'object') return false;
    if (seenSkillIds.has(s.skill_id)) return false;
    seenSkillIds.add(s.skill_id);
    if (!ALLOWED_SKILL_IDS.has(s.skill_id)) return false;
    if (!Number.isInteger(s.points) || !FIBONACCI_POINTS.has(s.points)) return false;
    computedTotal += s.points;
  }

  if (aiResponse.total_points !== computedTotal) return false;
  if ((aiResponse.response_type === 'invalid' || aiResponse.response_type === 'skipped') && (aiResponse.total_points !== 0 || aiResponse.skills_detected.length !== 0)) return false;

  return true;
}

function applyAiScoring(session, questionData, answerText, aiResponse) {
  if (!validateScoringResponse(aiResponse)) {
    console.error('AI schema error');
    session.answers.push({ questionId: questionData.id, answerText, status: 'ai_invalid_schema', responseType: 'invalid_schema', pointsEarned: 0 });
    return false;
  }

  if (aiResponse.response_type === 'valid') {
    for (const detection of aiResponse.skills_detected) {
      session.microSkillScores[detection.skill_id] += detection.points;
    }
    session.points.total += aiResponse.total_points;
    session.counters.questionsAnswered += 1;
    session.answers.push({ questionId: questionData.id, answerText, status: 'scored', responseType: 'valid', pointsEarned: aiResponse.total_points });
    return true;
  }

  if (aiResponse.response_type === 'invalid') {
    session.microSkillScores.INVALID += 1;
    session.counters.invalidAnswers += 1;
    session.counters.questionsAnswered += 1;
    session.answers.push({ questionId: questionData.id, answerText, status: 'scored', responseType: 'invalid', pointsEarned: 0 });
    return true;
  }

  session.answers.push({ questionId: questionData.id, answerText, status: 'scored', responseType: 'skipped', pointsEarned: 0 });
  return true;
}

function markAiFailure(session, questionData, answerText) {
  session.answers.push({ questionId: questionData.id, answerText, status: 'ai_failed', responseType: 'ai_failed', pointsEarned: 0 });
}

function getClientSession(session) {
  return {
    sessionId: session.sessionId,
    startedAt: session.startedAt,
    counters: session.counters,
    points: session.points,
    checkpoints: session.checkpoints,
    microSkillScores: session.microSkillScores
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
  markAiFailure,
  getClientSession,
  validateScoringResponse,
  ALLOWED_SKILL_IDS,
  FIBONACCI_POINTS,
  markAnswerSubmitted,
  markAnswerScored,
  isResultsReady
};

function markAnswerSubmitted(session) {
  session._lastAnswerSubmissionIndex += 1;
  return session._lastAnswerSubmissionIndex;
}

function markAnswerScored(session, answerIndex) {
  if (Number.isInteger(answerIndex) && answerIndex > session._lastScoredAnswerIndex) {
    session._lastScoredAnswerIndex = answerIndex;
  }
}

function isResultsReady(session) {
  return session.counters.questionsAnswered >= 15 && session._lastScoredAnswerIndex >= 15;
}

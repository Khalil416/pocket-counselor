const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const {
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
  markAnswerSubmitted,
  markAnswerScored,
  isResultsReady
} = require('./session');
const { checkForCheckpoint, getProfileLabel } = require('./checkpoint');
const api = require('./api');

const PORT = process.env.PORT || 8080;

function loadApiKeyFromProperties() {
  const propsCandidates = [
    path.join(process.cwd(), 'application.properties'),
    path.join(process.cwd(), 'backend', 'src', 'main', 'resources', 'application.properties'),
    path.join(__dirname, 'src', 'main', 'resources', 'application.properties')
  ];

  const keyPattern = /^\s*(openai\.apiKey|openai\.api\.key|openai\.api_key|gemini\.api\.key)\s*=\s*(.+)\s*$/im;
  const modelPattern = /^\s*(openai\.model|gemini\.model)\s*=\s*(.+)\s*$/im;

  for (const propsPath of propsCandidates) {
    if (!fs.existsSync(propsPath)) continue;
    try {
      const content = fs.readFileSync(propsPath, 'utf-8');
      const keyMatch = content.match(keyPattern);
      const modelMatch = content.match(modelPattern);
      return {
        apiKey: keyMatch ? keyMatch[2].trim() : '',
        model: modelMatch ? modelMatch[2].trim() : 'gpt-4.1-mini',
        source: propsPath
      };
    } catch (error) {
      console.error('[AI-CONFIG] Failed to read properties file', { propsPath, error: error.message });
    }
  }

  return { apiKey: '', model: 'gpt-4.1-mini', source: null };
}

function maskSecret(secret) {
  if (!secret) return '[missing]';
  const tail = secret.slice(-4);
  return `***${tail}`;
}

const propsConfig = loadApiKeyFromProperties();
const resolvedApiKey = process.env.OPENAI_API_KEY || process.env.GEMINI_API_KEY || propsConfig.apiKey;
const resolvedModel = process.env.OPENAI_MODEL || process.env.GEMINI_MODEL || propsConfig.model;
api.initialize({ apiKey: resolvedApiKey, model: resolvedModel });

const startupMode = (process.env.AI_MODE || 'mock').toLowerCase();
console.log(`[AI-CONFIG] AI_MODE=${startupMode}`);
console.log(`[AI-CONFIG] API key source=${process.env.OPENAI_API_KEY ? 'env:OPENAI_API_KEY' : (process.env.GEMINI_API_KEY ? 'env:GEMINI_API_KEY' : (propsConfig.source || 'none'))} key=${maskSecret(resolvedApiKey)} model=${resolvedModel}`);

const app = express();
app.use(cors());
app.use(express.json());
app.use(apiTrafficLogger);


function createPreview(value, maxLen = 400) {
  try {
    const raw = typeof value === 'string' ? value : JSON.stringify(value);
    if (raw.length <= maxLen) return raw;
    return `${raw.slice(0, maxLen)}... [truncated ${raw.length - maxLen} chars]`;
  } catch {
    return '[unserializable]';
  }
}

function apiTrafficLogger(req, res, next) {
  if (!req.path.startsWith('/api/')) return next();

  const started = Date.now();
  const reqBody = req.body && Object.keys(req.body).length ? createPreview(req.body) : '{}';
  console.log(`[API->IN ] ${req.method} ${req.originalUrl} body=${reqBody}`);

  const originalJson = res.json.bind(res);
  res.json = (body) => {
    const elapsed = Date.now() - started;
    const payload = createPreview(body);
    console.log(`[API<-OUT] ${req.method} ${req.originalUrl} status=${res.statusCode} ${elapsed}ms body=${payload}`);
    return originalJson(body);
  };

  next();
}

app.use(express.static(path.join(__dirname, '..', 'frontend')));

app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', ai_mode: process.env.AI_MODE || 'mock' });
});

app.get('/api/ai/status', (req, res) => {
  const status = api.getRuntimeStatus();
  res.json({
    mode: status.mode,
    keyLoaded: status.keyLoaded,
    promptFilesOk: status.promptFilesOk,
    model: status.model
  });
});

app.post('/api/session/start', (req, res) => {
  const session = createSession();
  const firstQuestion = getNextQuestion(session);
  res.json({ sessionId: session.sessionId, question: firstQuestion, session: getClientSession(session), canSkip: canSkip(session) });
});

app.post('/api/session/:id/answer', async (req, res) => {
  const session = getSession(req.params.id);
  if (!session) {
    return res.status(404).json({ error: 'session_not_found', message: 'Session not found' });
  }

  const { questionId, answer, answerText } = req.body || {};
  const normalizedAnswer = typeof answer === 'string' ? answer : answerText;

  if (!questionId || typeof normalizedAnswer !== 'string') {
    return res.status(400).json({
      error: 'invalid_answer_payload',
      message: 'questionId and answer text are required'
    });
  }

  if (normalizedAnswer.trim().length < 10) {
    return res.status(400).json({
      error: 'answer_too_short',
      message: 'Please share a bit more — at least 10 characters.'
    });
  }

  const questionData = getQuestionById(questionId);
  if (!questionData) {
    return res.status(400).json({ error: 'invalid_question_id', message: 'Invalid question ID' });
  }

  const answerSubmissionIndex = markAnswerSubmitted(session);
  void fireScoring(session, questionData, normalizedAnswer.trim(), answerSubmissionIndex);

  const nextQuestion = getNextQuestion(session);
  const forceFinishAt35 = session.counters.questionsShown >= 35;
  if (!nextQuestion || forceFinishAt35) finishSession(session);
  if (forceFinishAt35 && session.checkpoints.reached === 0) session._hasLowDataWarning = true;

  return res.json({
    nextQuestion,
    session: getClientSession(session),
    canSkip: canSkip(session),
    finished: !nextQuestion || forceFinishAt35,
    forcedResultsByLimit: forceFinishAt35,
    lowDataQualityWarning: forceFinishAt35 && session.checkpoints.reached === 0,
    answerSubmissionIndex,
    lastScoredAnswerIndex: session._lastScoredAnswerIndex,
    lastAnswerScored: session._lastScoredAnswerIndex >= answerSubmissionIndex,
    answeredCount: session.counters.questionsAnswered
  });
});

async function fireScoring(session, questionData, answerText, answerSubmissionIndex) {
  try {
    let aiResponse;
    try {
      aiResponse = await api.callScoringPrompt(questionData.text, answerText, questionData.expected_points);
    } catch (err) {
      aiResponse = await api.callScoringPrompt(questionData.text, answerText, questionData.expected_points);
    }

    const applied = applyAiScoring(session, questionData, answerText, aiResponse);
    if (!applied) return;
    markAnswerScored(session, answerSubmissionIndex);

    const checkpoint = checkForCheckpoint(session);
    if (checkpoint) session._pendingCheckpoint = checkpoint;

    if (session.counters.invalidAnswers >= 7) session._pendingWarning = 'invalid_hard';
    else if (session.counters.invalidAnswers >= 4) session._pendingWarning = 'invalid_soft';
  } catch (error) {
    console.error('[AI] scoring failed after retry:', error.message);
    markAiFailure(session, questionData, answerText);
  }
}

app.get('/api/session/:id/state', (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const checkpointReachedNow = Boolean(session._pendingCheckpoint);
  const payload = {
    checkpoint_reached_now: checkpointReachedNow,
    current_checkpoint: session.checkpoints.reached,
    checkpoint: session._pendingCheckpoint,
    warning: session._pendingWarning,
    low_data_quality_warning: session._hasLowDataWarning,
    scoring: {
      answers_submitted: session._lastAnswerSubmissionIndex,
      last_scored_answer_index: session._lastScoredAnswerIndex,
      results_ready: isResultsReady(session)
    },
    session: getClientSession(session)
  };

  session._pendingCheckpoint = null;
  session._pendingWarning = null;

  res.json(payload);
});

app.post('/api/session/:id/skip', (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { questionId } = req.body;
  if (!questionId) return res.status(400).json({ error: 'questionId is required' });
  if (!canSkip(session)) return res.status(400).json({ error: 'Maximum skips reached' });

  const maxSkipsReached = skipQuestion(session, questionId);
  const nextQuestion = getNextQuestion(session);
  const forceFinishAt35 = session.counters.questionsShown >= 35;
  if (!nextQuestion || forceFinishAt35) finishSession(session);
  if (forceFinishAt35 && session.checkpoints.reached === 0) session._hasLowDataWarning = true;

  res.json({ skipped: true, maxSkipsReached, nextQuestion, session: getClientSession(session), canSkip: canSkip(session), finished: !nextQuestion || forceFinishAt35 });
});

async function waitForResultsReadiness(session, timeoutMs = 4000, intervalMs = 100) {
  const start = Date.now();
  while (Date.now() - start <= timeoutMs) {
    if (isResultsReady(session)) return true;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  return isResultsReady(session);
}

async function handleSessionResults(req, res) {
  const session = getSession(req.params.id);
  if (!session) {
    return res.status(404).json({
      error: {
        code: 'SESSION_NOT_FOUND',
        message: 'Session not found.'
      }
    });
  }

  if (session._resultsRequested && session._resultsCache) return res.json(session._resultsCache);

  const ready = await waitForResultsReadiness(session);
  if (!ready) {
    return res.status(409).json({
      error: {
        code: 'RESULTS_NOT_READY',
        message: 'Results are not ready yet. Please retry once scoring catches up.'
      },
      readiness: {
        minimum_answered_required: 15,
        answered_count: session.counters.questionsAnswered,
        last_scored_answer_index: session._lastScoredAnswerIndex
      }
    });
  }

  finishSession(session);

  try {
    const aiResults = await api.callResultsPrompt(session);
    session._resultsRequested = true;
    session._resultsCache = aiResults;

    return res.json(aiResults);
  } catch (error) {
    console.error('Results generation failed:', error.message);
    const errorCode = error.code || (error.message === 'AI schema error' ? 'AI_SCHEMA_ERROR' : 'RESULTS_GENERATION_FAILED');
    return res.status(500).json({
      error: {
        code: errorCode,
        message: 'Results generation failed.'
      },
      fallback: {
        profile_quality: getProfileLabel(session.checkpoints.reached),
        overall_summary: 'Results could not be generated.',
        categories: [],
        strongest_areas: [],
        growth_areas: []
      }
    });
  }
}

app.get('/api/session/:id/results', handleSessionResults);
app.post('/api/session/:id/results', handleSessionResults);

app.get('/api/session/:id/answers', (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const answers = session.answers.map((entry) => {
    const question = getQuestionById(entry.questionId);
    return {
      questionId: entry.questionId,
      questionText: question?.text || `Question ${entry.questionId}`,
      answerText: entry.answerText || '',
      responseType: entry.responseType,
      pointsEarned: entry.pointsEarned
    };
  });

  return res.json({
    session: getClientSession(session),
    answers
  });
});

if (require.main === module) {
  app.listen(PORT, () => console.log(`Pocket Counselor server running on http://localhost:${PORT}`));
}

module.exports = { app, fireScoring };

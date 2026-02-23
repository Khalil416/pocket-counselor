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
  getClientSession
} = require('./session');
const { checkForCheckpoint, getProfileLabel } = require('./checkpoint');
const api = require('./api');

const PORT = process.env.PORT || 8080;

function loadApiKeyFromProperties() {
  try {
    const propsPath = path.join(__dirname, 'src', 'main', 'resources', 'application.properties');
    const content = fs.readFileSync(propsPath, 'utf-8');
    const match = content.match(/gemini\.api\.key=(.+)/);
    const modelMatch = content.match(/gemini\.model=(.+)/);
    return { apiKey: match ? match[1].trim() : '', model: modelMatch ? modelMatch[1].trim() : 'gemini-2.5-flash' };
  } catch {
    return { apiKey: '', model: 'gemini-2.5-flash' };
  }
}

const propsConfig = loadApiKeyFromProperties();
api.initialize({ apiKey: process.env.GEMINI_API_KEY || propsConfig.apiKey, model: process.env.GEMINI_MODEL || propsConfig.model });

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

app.post('/api/session/start', (req, res) => {
  const session = createSession();
  const firstQuestion = getNextQuestion(session);
  res.json({ sessionId: session.sessionId, question: firstQuestion, session: getClientSession(session), canSkip: canSkip(session) });
});

app.post('/api/session/:id/answer', async (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  const { questionId, answer } = req.body;
  if (!questionId || typeof answer !== 'string') return res.status(400).json({ error: 'questionId and answer are required' });
  if (answer.trim().length < 10) return res.status(400).json({ error: 'answer_too_short' });

  const questionData = getQuestionById(questionId);
  if (!questionData) return res.status(400).json({ error: 'Invalid question ID' });

  void fireScoring(session, questionData, answer.trim());

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
    lowDataQualityWarning: forceFinishAt35 && session.checkpoints.reached === 0
  });
});

async function fireScoring(session, questionData, answerText) {
  try {
    let aiResponse;
    try {
      aiResponse = await api.callScoringPrompt(questionData.text, answerText, questionData.expected_points);
    } catch (err) {
      aiResponse = await api.callScoringPrompt(questionData.text, answerText, questionData.expected_points);
    }

    const applied = applyAiScoring(session, questionData, answerText, aiResponse);
    if (!applied) return;

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

app.get('/api/session/:id/results', async (req, res) => {
  const session = getSession(req.params.id);
  if (!session) return res.status(404).json({ error: 'Session not found' });

  finishSession(session);
  if (session._resultsRequested && session._resultsCache) return res.json(session._resultsCache);

  try {
    const aiResults = await api.callResultsPrompt(session);
    session._resultsRequested = true;
    session._resultsCache = aiResults;

    return res.json(aiResults);
  } catch (error) {
    if (error.message === 'AI schema error') console.error('AI schema error');
    else console.error('Results generation failed:', error.message);
    return res.json({
      profile_quality: getProfileLabel(session.checkpoints.reached),
      overall_summary: 'Results could not be generated.',
      categories: [],
      strongest_areas: [],
      growth_areas: []
    });
  }
});

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

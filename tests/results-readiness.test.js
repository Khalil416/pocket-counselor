const test = require('node:test');
const assert = require('node:assert/strict');

const { app } = require('../backend/server');
const api = require('../backend/api');

process.env.AI_MODE = 'mock';

let server;
let base;

async function jfetch(path, options = {}) {
  const res = await fetch(`${base}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  });

  const body = await res.json().catch(() => ({}));
  return { status: res.status, body };
}

async function waitForCondition(check, attempts = 80, delayMs = 50) {
  for (let i = 0; i < attempts; i += 1) {
    const result = await check();
    if (result) return result;
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  throw new Error('Timed out waiting for condition');
}

async function submitAnswer(sessionId, questionId, answerText) {
  return jfetch(`/api/session/${sessionId}/answer`, {
    method: 'POST',
    body: JSON.stringify({ questionId, answerText })
  });
}

test.before(async () => {
  server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  await new Promise((resolve) => server.close(resolve));
});

test('results endpoint returns 200 + valid JSON once session is ready', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const sessionId = start.body.sessionId;
  let nextQuestion = start.body.question;

  for (let i = 0; i < 15; i += 1) {
    const answer = await submitAnswer(sessionId, nextQuestion.id, `Answer ${i + 1} with enough detail for scoring.`);
    assert.equal(answer.status, 200);
    nextQuestion = answer.body.nextQuestion;
  }

  await waitForCondition(async () => {
    const state = await jfetch(`/api/session/${sessionId}/state`);
    return state.body?.scoring?.results_ready ? state.body : null;
  });

  const results = await jfetch(`/api/session/${sessionId}/results`);
  assert.equal(results.status, 200);
  assert.equal(typeof results.body.profile_quality, 'string');
  assert.ok(Array.isArray(results.body.categories));
  assert.ok(Array.isArray(results.body.strongest_areas));
  assert.ok(Array.isArray(results.body.growth_areas));
});

test('after 15th answered submission, state reports results_ready checkpoint signal', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const sessionId = start.body.sessionId;
  let nextQuestion = start.body.question;

  for (let i = 0; i < 15; i += 1) {
    const answer = await submitAnswer(sessionId, nextQuestion.id, `Detailed response ${i + 1} for checkpoint readiness.`);
    assert.equal(answer.status, 200);
    nextQuestion = answer.body.nextQuestion;
  }

  const state = await waitForCondition(async () => {
    const current = await jfetch(`/api/session/${sessionId}/state`);
    if (current.body?.scoring?.results_ready) return current.body;
    return null;
  });

  assert.equal(state.session.counters.questionsAnswered >= 15, true);
  assert.equal(state.scoring.last_scored_answer_index >= 15, true);
  assert.equal(state.scoring.results_ready, true);
});

test('results before 15th scoring completion returns retryable 409 JSON error', async () => {
  const originalCallScoringPrompt = api.callScoringPrompt;
  api.callScoringPrompt = async (...args) => {
    const answerText = String(args[1] || '').toLowerCase();
    if (answerText.includes('answer 15')) {
      await new Promise((resolve) => setTimeout(resolve, 4500));
    }
    return originalCallScoringPrompt(...args);
  };

  try {
    const start = await jfetch('/api/session/start', { method: 'POST' });
    const sessionId = start.body.sessionId;
    let nextQuestion = start.body.question;

    for (let i = 0; i < 15; i += 1) {
      const answer = await submitAnswer(sessionId, nextQuestion.id, `Answer ${i + 1} with enough detail to score.`);
      assert.equal(answer.status, 200);
      nextQuestion = answer.body.nextQuestion;
    }

    const results = await jfetch(`/api/session/${sessionId}/results`);
    assert.equal(results.status, 409);
    assert.equal(results.body.error.code, 'RESULTS_NOT_READY');
    assert.equal(typeof results.body.error.message, 'string');
  } finally {
    api.callScoringPrompt = originalCallScoringPrompt;
  }
});


test('ai status endpoint reports gemini provider metadata', async () => {
  const status = await jfetch('/api/ai/status');
  assert.equal(status.status, 200);
  assert.equal(status.body.provider, 'gemini');
  assert.ok(['mock', 'real'].includes(status.body.mode));
  assert.equal(typeof status.body.keyLoaded, 'boolean');
  assert.equal(typeof status.body.model, 'string');
});

test('results endpoint returns 502 for AI schema errors', async () => {
  const originalCallResultsPrompt = api.callResultsPrompt;
  api.callResultsPrompt = async () => {
    const err = new Error('AI schema error');
    err.code = 'AI_SCHEMA_ERROR';
    err.details = 'MISSING_GROWTH_AREAS';
    throw err;
  };

  try {
    const start = await jfetch('/api/session/start', { method: 'POST' });
    const sessionId = start.body.sessionId;
    let nextQuestion = start.body.question;

    for (let i = 0; i < 15; i += 1) {
      const answer = await submitAnswer(sessionId, nextQuestion.id, `Answer ${i + 1} with enough detail for scoring.`);
      assert.equal(answer.status, 200);
      nextQuestion = answer.body.nextQuestion;
    }

    await waitForCondition(async () => {
      const state = await jfetch(`/api/session/${sessionId}/state`);
      return state.body?.scoring?.results_ready ? state.body : null;
    });

    const results = await jfetch(`/api/session/${sessionId}/results`);
    assert.equal(results.status, 502);
    assert.equal(results.body.error.code, 'AI_SCHEMA_ERROR');
    assert.equal(results.body.details, 'MISSING_GROWTH_AREAS');
  } finally {
    api.callResultsPrompt = originalCallResultsPrompt;
  }
});

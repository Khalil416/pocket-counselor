const test = require('node:test');
const assert = require('node:assert/strict');

const { app } = require('../backend/server');
const { getSession } = require('../backend/session');

process.env.AI_MODE = 'mock';

let server;
let base;

async function jfetch(path, options = {}) {
  const res = await fetch(`${base}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  });

  const body = await res.json();
  return { status: res.status, body };
}

test.before(async () => {
  server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  await new Promise((resolve) => server.close(resolve));
});

test('start session returns first question', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });

  assert.equal(start.status, 200);
  assert.ok(start.body.sessionId);
  assert.equal(start.body.question.questionNumber, 1);
  assert.equal(typeof start.body.question.id, 'number');
  assert.equal(typeof start.body.question.text, 'string');
});

test('submit first answer returns next question without blocking', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });

  const submit = await jfetch(`/api/session/${start.body.sessionId}/answer`, {
    method: 'POST',
    body: JSON.stringify({
      questionId: start.body.question.id,
      answerText: 'This is the first answer and it is comfortably longer than ten characters.'
    })
  });

  assert.equal(submit.status, 200);
  assert.equal(submit.body.finished, false);
  assert.ok(submit.body.nextQuestion);
  assert.equal(submit.body.nextQuestion.questionNumber, 2);
});

test('short answer (<10 chars) is rejected without advancing', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });

  const submit = await jfetch(`/api/session/${start.body.sessionId}/answer`, {
    method: 'POST',
    body: JSON.stringify({ questionId: start.body.question.id, answerText: 'too short' })
  });

  assert.equal(submit.status, 400);
  assert.equal(submit.body.error, 'answer_too_short');
  assert.ok(submit.body.message);

  const state = await jfetch(`/api/session/${start.body.sessionId}/state`);
  assert.equal(state.body.session.counters.questionsShown, 1);
  assert.equal(state.body.session.counters.questionsAnswered, 0);
});

test('AI/scoring failure does not block progression and records ai_failed', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const sessionId = start.body.sessionId;

  const submit = await jfetch(`/api/session/${sessionId}/answer`, {
    method: 'POST',
    body: JSON.stringify({
      questionId: start.body.question.id,
      answerText: 'Trigger retry then fail [FORCE_AI_FAIL] while still moving forward.'
    })
  });

  assert.equal(submit.status, 200);
  assert.ok(submit.body.nextQuestion);
  assert.equal(submit.body.nextQuestion.questionNumber, 2);

  for (let i = 0; i < 20; i++) {
    const live = getSession(sessionId);
    if (live.answers.some((entry) => entry.status === 'ai_failed')) {
      assert.equal(live.answers[live.answers.length - 1].status, 'ai_failed');
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 20));
  }

  assert.fail('Expected ai_failed status to be recorded');
});

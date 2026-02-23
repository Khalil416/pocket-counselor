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

async function waitForAnswered(sessionId, expected) {
  for (let i = 0; i < 50; i++) {
    const state = await jfetch(`/api/session/${sessionId}/state`);
    if (state.body.session.counters.questionsAnswered >= expected) return state.body;
    await new Promise((r) => setTimeout(r, 20));
  }
  throw new Error('timeout waiting answered');
}

test.before(async () => {
  server = app.listen(0);
  await new Promise((r) => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});

test.after(async () => {
  await new Promise((r) => server.close(r));
});

test('1) Session init', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  assert.equal(start.status, 200);
  assert.equal(start.body.session.microSkillScores.INVALID, 0);
  const vals = Object.values(start.body.session.microSkillScores);
  assert.ok(vals.every((v) => v === 0));
});

test('2) Skip behavior max 7 and no answered/checkpoint trigger', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  let q = start.body.question;

  for (let i = 0; i < 7; i++) {
    const skip = await jfetch(`/api/session/${id}/skip`, { method: 'POST', body: JSON.stringify({ questionId: q.id }) });
    assert.equal(skip.status, 200);
    q = skip.body.nextQuestion;
  }

  const s = await jfetch(`/api/session/${id}/state`);
  assert.equal(s.body.session.counters.questionsSkipped, 7);
  assert.equal(s.body.session.counters.questionsAnswered, 0);
  assert.equal(s.body.current_checkpoint, 0);

  const blocked = await jfetch(`/api/session/${id}/skip`, { method: 'POST', body: JSON.stringify({ questionId: q.id }) });
  assert.equal(blocked.status, 400);
});

test('3) <10 chars blocks progression and AI call', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  const short = await jfetch(`/api/session/${id}/answer`, { method: 'POST', body: JSON.stringify({ questionId: start.body.question.id, answer: 'kisa' }) });
  assert.equal(short.status, 400);

  const state = await jfetch(`/api/session/${id}/state`);
  assert.equal(state.body.session.counters.questionsAnswered, 0);
  assert.equal(state.body.session.counters.questionsShown, 1);
});

test('4) valid scoring increments skill/total/answered', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  await jfetch(`/api/session/${id}/answer`, {
    method: 'POST',
    body: JSON.stringify({ questionId: start.body.question.id, answer: 'Bu cevap yeterince uzun ve değerlendirilebilir.' })
  });

  const state = await waitForAnswered(id, 1);
  assert.equal(state.session.points.total, 8);
  assert.equal(state.session.microSkillScores.empathy, 5);
  assert.equal(state.session.microSkillScores.planning, 3);
  assert.equal(state.session.counters.questionsAnswered, 1);
});

test('5) invalid scoring increments invalid counters only', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  await jfetch(`/api/session/${id}/answer`, {
    method: 'POST',
    body: JSON.stringify({ questionId: start.body.question.id, answer: 'Bu cevap [FORCE_INVALID] tetikleyicisi ile invalid olur.' })
  });

  const state = await waitForAnswered(id, 1);
  assert.equal(state.session.counters.invalidAnswers, 1);
  assert.equal(state.session.microSkillScores.INVALID, 1);
  assert.equal(state.session.points.total, 0);
});

test('6) checkpoint only at answered%3 and not repeated', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  let q = start.body.question;

  for (let i = 0; i < 12; i++) {
    const resp = await jfetch(`/api/session/${id}/answer`, { method: 'POST', body: JSON.stringify({ questionId: q.id, answer: 'Uzun cevap metni [FORCE_HIGH] checkpoint testi için yazılıyor.' }) });
    q = resp.body.nextQuestion;
    if (!q) break;
  }

  const state1 = await waitForAnswered(id, 12);
  assert.ok(state1.current_checkpoint >= 1);
  const state2 = await jfetch(`/api/session/${id}/state`);
  assert.equal(state2.body.checkpoint_reached_now, false);
});

test('7) AI failure -> one retry then ai_failed entry and flow continues', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  const fail = await jfetch(`/api/session/${id}/answer`, {
    method: 'POST',
    body: JSON.stringify({ questionId: start.body.question.id, answer: 'Bu cevap [FORCE_AI_FAIL] ile hata üretir.' })
  });
  assert.equal(fail.status, 200);

  await new Promise((r) => setTimeout(r, 100));
  const state = await jfetch(`/api/session/${id}/state`);
  const live = getSession(id);
  assert.equal(live.counters.questionsAnswered, 0);
  assert.equal(live.answers[live.answers.length - 1].status, 'ai_failed');
});

test('8) 35 question limit forces results mode', async () => {
  const start = await jfetch('/api/session/start', { method: 'POST' });
  const id = start.body.sessionId;
  let q = start.body.question;
  let last;
  for (let i = 0; i < 35; i++) {
    last = await jfetch(`/api/session/${id}/answer`, { method: 'POST', body: JSON.stringify({ questionId: q.id, answer: 'Bu cevap 35 soru limitini test etmek için yeterince uzundur.' }) });
    q = last.body.nextQuestion;
    if (!q) break;
  }
  assert.equal(last.body.finished, true);
});

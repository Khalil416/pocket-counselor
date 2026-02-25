const test = require('node:test');
const assert = require('node:assert/strict');

const {
  PocketCounselorApiClient,
  ParseError,
  SchemaError,
  FlowError,
  RateLimitError
} = require('../frontend/api-client.js');

function makeJsonResponse(status, body, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...headers
    }
  });
}

test('retries 500 then succeeds and keeps idempotency key', async () => {
  const originalFetch = global.fetch;
  const originalRandom = Math.random;
  try {
    Math.random = () => 0;
    const calls = [];
    global.fetch = async (_url, options) => {
      calls.push(options.headers.get('Idempotency-Key'));
      if (calls.length === 1) {
        return makeJsonResponse(500, { error: 'temporary' });
      }
      return makeJsonResponse(200, { ok: true });
    };

    const client = new PocketCounselorApiClient({ baseDelayMs: 1, maxDelayMs: 2, maxTotalWaitMs: 1000 });
    const response = await client.request('/api/session/demo/answer', { method: 'POST', body: { questionId: 1, answerText: 'hello world' } });

    assert.equal(response.data.ok, true);
    assert.equal(calls.length, 2);
    assert.equal(calls[0], calls[1]);
  } finally {
    global.fetch = originalFetch;
    Math.random = originalRandom;
  }
});

test('respects Retry-After and throws RateLimitError after budget', async () => {
  const originalFetch = global.fetch;
  try {
    let attempts = 0;
    global.fetch = async () => {
      attempts += 1;
      return makeJsonResponse(429, { error: 'too_many_requests' }, { 'Retry-After': '0' });
    };

    const client = new PocketCounselorApiClient({ baseDelayMs: 1, maxDelayMs: 2, maxAttempts: 2, maxTotalWaitMs: 1000 });

    await assert.rejects(
      client.request('/api/session/start', { method: 'POST' }),
      (error) => error instanceof RateLimitError
    );
    assert.equal(attempts, 2);
  } finally {
    global.fetch = originalFetch;
  }
});

test('throws ParseError on invalid JSON and preserves preview', async () => {
  const originalFetch = global.fetch;
  try {
    global.fetch = async () => new Response('{invalid', { status: 200, headers: { 'Content-Type': 'application/json' } });
    const client = new PocketCounselorApiClient({ maxBodyPreviewChars: 5 });

    await assert.rejects(
      client.request('/api/session/start', { method: 'POST' }),
      (error) => error instanceof ParseError && typeof error.details.rawBodyPreview === 'string'
    );
  } finally {
    global.fetch = originalFetch;
  }
});

test('throws SchemaError when /start misses required question fields', async () => {
  const originalFetch = global.fetch;
  try {
    global.fetch = async () => makeJsonResponse(200, { sessionId: 's1' });
    const client = new PocketCounselorApiClient();

    await assert.rejects(
      client.startSession(),
      (error) => error instanceof SchemaError
    );
  } finally {
    global.fetch = originalFetch;
  }
});

test('reconciles mismatch via /state and throws FlowError when unclear', async () => {
  const originalFetch = global.fetch;
  try {
    global.fetch = async (url) => {
      if (url.includes('/state')) {
        return makeJsonResponse(200, { session: { currentQuestion: { id: 50 } } });
      }
      return makeJsonResponse(200, { nextQuestion: { id: 2 } });
    };

    const client = new PocketCounselorApiClient();
    client.updateSessionQuestion('s1', 10);

    await assert.rejects(
      client.submitAnswer('s1', { questionId: 20, answerText: 'long enough answer text' }),
      (error) => error instanceof FlowError
    );
  } finally {
    global.fetch = originalFetch;
  }
});

test('trims oversized answer and retries once on size error', async () => {
  const originalFetch = global.fetch;
  try {
    const submitted = [];
    global.fetch = async (url, options) => {
      if (url.includes('/state')) {
        return makeJsonResponse(200, { session: { currentQuestion: { id: 1 } } });
      }
      const body = JSON.parse(options.body);
      submitted.push(body.answerText);
      if (submitted.length === 1) {
        return makeJsonResponse(413, { error: 'payload_size_exceeded' });
      }
      return makeJsonResponse(200, { nextQuestion: { id: 2 }, canSkip: true });
    };

    const client = new PocketCounselorApiClient({ maxAnswerChars: 30, baseDelayMs: 1, maxDelayMs: 2 });
    client.updateSessionQuestion('s1', 1);
    const answer = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';

    const result = await client.submitAnswer('s1', { questionId: 1, answerText: answer });

    assert.equal(result.nextQuestion.id, 2);
    assert.equal(submitted.length, 2);
    assert.match(submitted[0], /\[trimmed\]$/);
    assert.ok(submitted[1].length <= submitted[0].length);
  } finally {
    global.fetch = originalFetch;
  }
});

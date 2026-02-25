const test = require('node:test');
const assert = require('node:assert/strict');

const api = require('../backend/api');

const originalFetch = global.fetch;
const originalAiMode = process.env.AI_MODE;

test.afterEach(() => {
  global.fetch = originalFetch;
  if (originalAiMode === undefined) delete process.env.AI_MODE;
  else process.env.AI_MODE = originalAiMode;
  api.initialize({ apiKey: '', model: 'gpt-4.1-mini' });
});

test('mock mode does not perform outbound network call for scoring', async () => {
  process.env.AI_MODE = 'mock';
  api.initialize({ apiKey: 'mock-key-not-used', model: 'gpt-4.1-mini' });

  let fetchCalled = false;
  global.fetch = async () => {
    fetchCalled = true;
    throw new Error('fetch should not be called in mock mode');
  };

  const result = await api.callScoringPrompt('Question?', 'This is a valid mocked answer text.', {
    minimum: 10,
    target: 20,
    excellent: 30
  });

  assert.equal(fetchCalled, false);
  assert.equal(result.response_type, 'valid');
});

test('real mode performs outbound OpenAI call for scoring when key is configured', async () => {
  process.env.AI_MODE = 'real';
  api.initialize({ apiKey: 'sk-test-123456', model: 'gpt-4.1-mini' });

  let capturedRequest;
  global.fetch = async (url, options) => {
    capturedRequest = { url, options };
    return {
      ok: true,
      json: async () => ({
        choices: [
          {
            message: {
              content: JSON.stringify({
                response_type: 'valid',
                total_points: 8,
                skills_detected: [
                  { skill_id: 'empathy', points: 5 },
                  { skill_id: 'planning', points: 3 }
                ]
              })
            }
          }
        ]
      })
    };
  };

  const result = await api.callScoringPrompt('Question?', 'This is a real mode answer text.', {
    minimum: 10,
    target: 20,
    excellent: 30
  });

  assert.equal(result.total_points, 8);
  assert.ok(capturedRequest.url.endsWith('/chat/completions'));
  assert.equal(capturedRequest.options.method, 'POST');
  assert.match(capturedRequest.options.headers.Authorization, /^Bearer sk-test-123456$/);
});

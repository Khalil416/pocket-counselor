const test = require('node:test');
const assert = require('node:assert/strict');

const api = require('../backend/api');

const originalFetch = global.fetch;
const originalAiMode = process.env.AI_MODE;

function buildSessionForResults() {
  return {
    counters: { questionsAnswered: 15, questionsSkipped: 0, invalidAnswers: 0 },
    points: { total: 80 },
    checkpoints: { reached: 2 },
    microSkillScores: { empathy: 20, planning: 15, INVALID: 0 }
  };
}

test.afterEach(() => {
  global.fetch = originalFetch;
  if (originalAiMode === undefined) delete process.env.AI_MODE;
  else process.env.AI_MODE = originalAiMode;
  api.initialize({ apiKey: '', model: 'gemini-2.5-flash' });
});

test('mock mode does not perform outbound network call for scoring', async () => {
  process.env.AI_MODE = 'mock';
  api.initialize({ apiKey: 'mock-key-not-used', model: 'gemini-2.5-flash' });

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

test('real mode performs outbound Gemini call for scoring when key is configured', async () => {
  process.env.AI_MODE = 'real';
  api.initialize({ apiKey: 'gm-test-123456', model: 'gemini-2.5-flash' });

  let capturedRequest;
  global.fetch = async (url, options) => {
    capturedRequest = { url, options };
    return {
      ok: true,
      json: async () => ({
        candidates: [
          {
            content: {
              parts: [{
                text: JSON.stringify({
                  response_type: 'valid',
                  total_points: 8,
                  skills_detected: [
                    { skill_id: 'empathy', points: 5 },
                    { skill_id: 'planning', points: 3 }
                  ]
                })
              }]
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
  assert.ok(capturedRequest.url.includes('generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent'));
  assert.equal(capturedRequest.options.method, 'POST');
  assert.equal(capturedRequest.options.headers['x-goog-api-key'], 'gm-test-123456');
});

test('defaults to real mode when AI_MODE is not set', async () => {
  delete process.env.AI_MODE;
  api.initialize({ apiKey: 'gm-test-123456', model: 'gemini-2.5-flash' });

  let fetchCalled = false;
  global.fetch = async () => {
    fetchCalled = true;
    return {
      ok: true,
      json: async () => ({
        candidates: [
          {
            content: {
              parts: [{ text: JSON.stringify({ response_type: 'valid', total_points: 8, skills_detected: [] }) }]
            }
          }
        ]
      })
    };
  };

  const result = await api.callScoringPrompt('Question?', 'This should use real mode by default.', {
    minimum: 10,
    target: 20,
    excellent: 30
  });

  assert.equal(fetchCalled, true);
  assert.equal(result.response_type, 'valid');
});

test('scoring handles invalid JSON from Gemini safely as schema_invalid', async () => {
  process.env.AI_MODE = 'real';
  api.initialize({ apiKey: 'gm-test-123456', model: 'gemini-2.5-flash' });

  global.fetch = async () => ({
    ok: true,
    json: async () => ({
      candidates: [{ content: { parts: [{ text: '{not-json' }] } }]
    })
  });

  const result = await api.callScoringPrompt('Question?', 'A detailed answer text.', {
    minimum: 10,
    target: 20,
    excellent: 30
  });

  assert.equal(result.response_type, 'schema_invalid');
  assert.equal(result.total_points, 0);
  assert.deepEqual(result.skills_detected, []);
});

test('results schema requires growth_areas and rejects needs_attention payload', async () => {
  process.env.AI_MODE = 'real';
  api.initialize({ apiKey: 'gm-test-123456', model: 'gemini-2.5-flash' });

  global.fetch = async () => ({
    ok: true,
    json: async () => ({
      candidates: [{
        content: {
          parts: [{
            text: JSON.stringify({
              profile_quality: 'Good',
              overall_summary: 'Özet metni.',
              categories: [{ name: 'Kategori', score: 50, label: 'Good', explanation: 'Açıklama.' }],
              strongest_areas: [{ skill_name: 'Empati', reason: 'Sebep.' }],
              needs_attention: [{ skill_name: 'Planlama', reason: 'Sebep.' }]
            })
          }]
        }
      }]
    })
  });

  await assert.rejects(
    () => api.callResultsPrompt(buildSessionForResults()),
    (error) => error && error.code === 'AI_SCHEMA_ERROR'
  );
});

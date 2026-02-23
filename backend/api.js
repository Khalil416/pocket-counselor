const fs = require('fs');
const path = require('path');

const scoringPromptPath = path.join(__dirname, '..', 'prompts', 'scoring.txt');
const resultsPromptPath = path.join(__dirname, '..', 'prompts', 'results.txt');
const SCORING_PROMPT = fs.readFileSync(scoringPromptPath, 'utf-8');
const RESULTS_PROMPT = fs.readFileSync(resultsPromptPath, 'utf-8');

const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta';
let API_KEY = '';
let MODEL_NAME = 'gemini-2.5-flash';

function initialize(config = {}) {
  if (config.apiKey) API_KEY = config.apiKey;
  if (config.model) MODEL_NAME = config.model;
}

function isMockMode() {
  return (process.env.AI_MODE || 'mock').toLowerCase() === 'mock';
}


function safePreview(value, maxLen = 300) {
  try {
    const text = typeof value === 'string' ? value : JSON.stringify(value);
    if (text.length <= maxLen) return text;
    return `${text.slice(0, maxLen)}... [truncated ${text.length - maxLen} chars]`;
  } catch {
    return '[unserializable]';
  }
}

function traceAiTraffic(direction, payload) {
  console.log(`[AI-${direction}] ${safePreview(payload)}`);
}

async function callGemini(promptText) {
  if (!API_KEY) throw new Error('Gemini API key not configured.');

  const systemMatch = promptText.match(/^SYSTEM:\s*([\s\S]*?)(?=\nUSER:|$)/);
  const userMatch = promptText.match(/USER:[\s\S]*$/);
  const systemText = systemMatch ? systemMatch[1].trim() : '';
  const userText = userMatch ? userMatch[0].replace(/^USER:\s*/, '').trim() : promptText;

  const url = `${GEMINI_API_BASE}/models/${MODEL_NAME}:generateContent?key=${API_KEY}`;
  const body = {
    system_instruction: { parts: [{ text: systemText }] },
    contents: [{ role: 'user', parts: [{ text: userText }] }],
    generationConfig: { temperature: 0.2, responseMimeType: 'application/json' }
  };

  traceAiTraffic('OUT', { url, model: MODEL_NAME, body });
  const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`Gemini API error ${response.status}: ${await response.text()}`);

  const data = await response.json();
  traceAiTraffic('IN', data);
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) throw new Error('Empty response from Gemini API');
  return JSON.parse(text.trim());
}

function buildMockScoring(answerText) {
  const trimmed = answerText.trim().toLowerCase();
  if (trimmed.includes('[force_ai_fail]')) throw new Error('forced-failure');
  if (trimmed.includes('[force_invalid_schema]')) return { foo: 'bar' };
  if (trimmed.includes('[force_invalid]')) return { response_type: 'invalid', total_points: 0, skills_detected: [] };
  if (trimmed.includes('[force_high]')) return {
    response_type: 'valid',
    total_points: 26,
    skills_detected: [{ skill_id: 'empathy', points: 13 }, { skill_id: 'planning', points: 13 }]
  };

  return {
    response_type: 'valid',
    total_points: 8,
    skills_detected: [{ skill_id: 'empathy', points: 5 }, { skill_id: 'planning', points: 3 }]
  };
}

function buildMockResults(session) {
  return {
    profile_quality: session.checkpoints.reached >= 2 ? 'Good' : 'Basic',
    overall_summary: 'Mock değerlendirme: cevaplarınıza göre beceri sinyalleri düzenli şekilde gözlendi.',
    categories: [
      { name: 'İletişim ve İnsan Becerileri', score: 68, label: 'Good', explanation: 'Yanıtlarda empati ve dinleme davranışları tekrarlandı.' },
      { name: 'Planlama ve Öz Yönetim', score: 61, label: 'Good', explanation: 'Cevaplarda planlı ve önceliklendirilmiş yaklaşım görüldü.' }
    ],
    strongest_areas: [{ skill_name: 'Empati', reason: 'Birden çok yanıtta başkasının perspektifini ele aldınız.' }],
    growth_areas: [{ skill_name: 'Sayısal Muhakeme', reason: 'Veri/sayı temelli örnekler daha az görünür kaldı.' }]
  };
}

async function callScoringPrompt(questionText, answerText, expectedPoints) {
  if (isMockMode()) return buildMockScoring(answerText);

  let prompt = SCORING_PROMPT;
  prompt = prompt.replace('{question_text}', questionText);
  prompt = prompt.replace('{min}', String(expectedPoints.minimum));
  prompt = prompt.replace('{target}', String(expectedPoints.target));
  prompt = prompt.replace('{excellent}', String(expectedPoints.excellent));
  prompt = prompt.replace('{answer_text}', answerText);
  return callGemini(prompt);
}

async function callResultsPrompt(session) {
  if (isMockMode()) return buildMockResults(session);

  const scoresObj = { ...session.microSkillScores };
  delete scoresObj.INVALID;

  let prompt = RESULTS_PROMPT;
  prompt = prompt.replace('{questions_answered}', String(session.counters.questionsAnswered));
  prompt = prompt.replace('{questions_skipped}', String(session.counters.questionsSkipped));
  prompt = prompt.replace('{invalid_answers}', String(session.counters.invalidAnswers));
  prompt = prompt.replace('{total_points}', String(session.points.total));
  prompt = prompt.replace('{checkpoint_number}', String(session.checkpoints.reached));
  prompt = prompt.replace('{micro_skill_scores}', JSON.stringify(scoresObj, null, 2));
  return callGemini(prompt);
}

module.exports = { initialize, callScoringPrompt, callResultsPrompt, isMockMode };

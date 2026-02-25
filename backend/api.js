const fs = require('fs');
const path = require('path');

const scoringPromptPath = path.join(__dirname, '..', 'prompts', 'scoring.txt');
const resultsPromptPath = path.join(__dirname, '..', 'prompts', 'results.txt');
const RESULT_PROFILE_QUALITY = new Set(['Basic', 'Good', 'Very Detailed', 'Maximum']);
const RESULT_CATEGORY_LABELS = new Set(['Weak', 'Basic', 'Good', 'Strong', 'Exceptional']);
const DEBUG_LOGS_ENABLED = process.env.NODE_ENV !== 'production';

let API_KEY = '';
let MODEL_NAME = 'gemini-2.5-flash';

function initialize(config = {}) {
  API_KEY = config.apiKey || '';
  MODEL_NAME = config.model || MODEL_NAME;
}

function isMockMode() {
  return (process.env.AI_MODE || 'real').toLowerCase() === 'mock';
}

function readPromptFile(promptPath) {
  const candidates = [
    promptPath,
    path.join(process.cwd(), 'prompts', path.basename(promptPath))
  ];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      console.log(`[AI-CONFIG] Loaded prompt file: ${candidate}`);
      return fs.readFileSync(candidate, 'utf-8');
    }
  }

  const error = new Error(`Prompt file not found: ${path.basename(promptPath)}`);
  error.code = 'PROMPT_FILE_NOT_FOUND';
  throw error;
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
  if (!DEBUG_LOGS_ENABLED) return;
  console.log(`[AI-${direction}] ${safePreview(payload)}`);
}

function parseJsonFromText(rawText) {
  const text = String(rawText || '').trim();
  if (!text) {
    const error = new Error('Empty response from Gemini API');
    error.code = 'AI_SCHEMA_ERROR';
    throw error;
  }

  try {
    return JSON.parse(text);
  } catch (cause) {
    const error = new Error('AI schema error');
    error.code = 'AI_SCHEMA_ERROR';
    error.details = 'INVALID_JSON';
    error.cause = cause;
    throw error;
  }
}

function shouldEnableForceTokens() {
  const mode = (process.env.AI_MODE || 'real').toLowerCase();
  return process.env.NODE_ENV === 'test' || mode === 'mock' || process.env.ENABLE_TEST_TOKENS === 'true';
}

async function callGemini(promptText) {
  if (!API_KEY) {
    const error = new Error('Gemini API key not configured.');
    error.code = 'AI_KEY_MISSING';
    throw error;
  }

  const url = `https://generativelanguage.googleapis.com/v1beta/models/${MODEL_NAME}:generateContent`;
  const body = {
    contents: [
      {
        role: 'user',
        parts: [{ text: promptText }]
      }
    ],
    generationConfig: {
      temperature: 0,
      responseMimeType: 'application/json'
    }
  };

  console.log('[AI] Request started', { provider: 'gemini', model: MODEL_NAME });
  console.log('[AI] Calling Gemini generateContent');
  traceAiTraffic('OUT', { url, model: MODEL_NAME, contentsCount: body.contents.length });
  const timeoutMs = Number(process.env.GEMINI_TIMEOUT_MS || 20000);
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), Number.isFinite(timeoutMs) && timeoutMs > 0 ? timeoutMs : 20000);

  let response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': API_KEY
      },
      body: JSON.stringify(body),
      signal: controller.signal
    });
  } catch (error) {
    if (error && error.name === 'AbortError') {
      const timeoutError = new Error(`Gemini request timed out after ${timeoutMs}ms`);
      timeoutError.code = 'AI_TIMEOUT';
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }

  if (!response.ok) {
    const raw = await response.text();
    console.error('[AI] Gemini request failed', { status: response.status, body: safePreview(raw, 800) });
    throw new Error(`Gemini API error ${response.status}: ${raw}`);
  }

  const data = await response.json();
  console.log('[AI] Gemini response received');
  traceAiTraffic('IN', data);
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  if (!text) {
    const error = new Error('Empty response from Gemini API');
    error.code = 'AI_SCHEMA_ERROR';
    throw error;
  }

  return parseJsonFromText(text);
}

function buildMockScoring(answerText) {
  const trimmed = answerText.trim().toLowerCase();
  if (shouldEnableForceTokens() && trimmed.includes('[force_ai_fail]')) throw new Error('forced-failure');
  if (shouldEnableForceTokens() && trimmed.includes('[force_invalid_schema]')) return { foo: 'bar' };
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
    overall_summary: 'Yanıtlarınıza göre iletişim ve planlama odaklı bir profil gözleniyor. Cevaplarınızda yapılandırılmış düşünme ve insan odaklı yaklaşım birlikte öne çıkıyor. Bazı alanlarda daha fazla örnek verildiğinde profiliniz daha da netleşebilir.',
    categories: [
      { name: 'İletişim ve İnsan Becerileri', score: 71, label: 'Strong', explanation: 'Yanıtlarınızda empati ve aktif dinleme davranışları düzenli biçimde gözleniyor. Özellikle karşı tarafı anlama ve uygun cevap üretme yaklaşımınız dikkat çekiyor.' },
      { name: 'Planlama ve Öz Yönetim', score: 63, label: 'Good', explanation: 'Cevaplarınıza göre planlama ve önceliklendirme tarafında tutarlı sinyaller var. Görevleri adımlara bölme yaklaşımınız düzenli bir çalışma tarzına işaret ediyor.' },
      { name: 'Uyum ve Öğrenme Yaklaşımı', score: 48, label: 'Good', explanation: 'Farklı durumlara uyum sağlama yönünde temel düzeyde olumlu göstergeler gözleniyor. Daha fazla somut örnekle bu alanın gücü daha net görülebilir.' },
      { name: 'Analitik Derinlik', score: 34, label: 'Basic', explanation: 'Analitik muhakeme sinyalleri bazı yanıtlarda görülse de daha sınırlı kalmış görünüyor. Veri veya neden-sonuç temelli örneklerin artması bu kategoriyi güçlendirebilir.' }
    ],
    strongest_areas: [
      { skill_name: 'Empati', reason: 'Cevaplarınıza göre farklı bakış açılarını dikkate alma davranışı düzenli olarak gözleniyor.' },
      { skill_name: 'Planlama', reason: 'Gözlenen örneklerde işleri adım adım yapılandırma yaklaşımınız belirgin şekilde öne çıkıyor.' },
      { skill_name: 'Aktif Dinleme', reason: 'Yanıtlarınıza göre karşı tarafın ihtiyacını anlayıp buna uygun tepki üretme eğilimi güçlü görünüyor.' }
    ],
    growth_areas: [
      { skill_name: 'Sayısal Muhakeme', reason: 'Bu alana dair sinyaller cevaplarınıza göre daha az görünmüş durumda. Sayısal gerekçeli örnekler fırsat buldukça profili destekleyebilir.' },
      { skill_name: 'Deneysel Yaklaşım', reason: 'Farklı yöntemleri test etmeye dair örnekler sınırlı gözleniyor. Küçük denemeler ve iterasyon anlatıları bu alanı görünür kılabilir.' },
      { skill_name: 'Müzakere', reason: 'Karşılıklı uzlaşma süreçlerine dair örnekler cevaplarınıza göre daha az yer bulmuş. Somut müzakere senaryoları bu beceriyi daha iyi yansıtabilir.' }
    ]
  };
}

function validateResultsResponse(aiResponse) {
  if (!aiResponse || typeof aiResponse !== 'object' || Array.isArray(aiResponse)) return { valid: false, reason: 'INVALID_RESULTS_OBJECT' };
  if ('needs_attention' in aiResponse) return { valid: false, reason: 'UNSUPPORTED_FIELD_NEEDS_ATTENTION' };
  if (!RESULT_PROFILE_QUALITY.has(aiResponse.profile_quality)) return { valid: false, reason: 'INVALID_PROFILE_QUALITY' };
  if (typeof aiResponse.overall_summary !== 'string') return { valid: false, reason: 'MISSING_OVERALL_SUMMARY' };
  if (!Array.isArray(aiResponse.categories)) return { valid: false, reason: 'MISSING_CATEGORIES' };
  if (!Array.isArray(aiResponse.strongest_areas)) return { valid: false, reason: 'MISSING_STRONGEST_AREAS' };
  if (!Array.isArray(aiResponse.growth_areas)) return { valid: false, reason: 'MISSING_GROWTH_AREAS' };

  for (const category of aiResponse.categories) {
    if (!category || typeof category !== 'object' || Array.isArray(category)) return { valid: false, reason: 'INVALID_CATEGORY_OBJECT' };
    if (typeof category.name !== 'string' || typeof category.explanation !== 'string') return { valid: false, reason: 'INVALID_CATEGORY_TEXT' };
    if (!Number.isInteger(category.score) || category.score < 0 || category.score > 100) return { valid: false, reason: 'NON_INT_SCORE' };
    if (!RESULT_CATEGORY_LABELS.has(category.label)) return { valid: false, reason: 'INVALID_CATEGORY_LABEL' };
  }

  for (const area of [...aiResponse.strongest_areas, ...aiResponse.growth_areas]) {
    if (!area || typeof area !== 'object' || Array.isArray(area)) return { valid: false, reason: 'INVALID_AREA_OBJECT' };
    if (typeof area.skill_name !== 'string' || typeof area.reason !== 'string') return { valid: false, reason: 'INVALID_AREA_TEXT' };
  }

  return { valid: true, reason: null };
}

async function callScoringPrompt(questionText, answerText, expectedPoints) {
  if (isMockMode()) return buildMockScoring(answerText);
  console.log('[AI] AI_MODE=real -> calling Gemini scoring');

  const systemPrompt = readPromptFile(scoringPromptPath);
  const userMessage = [
    `QUESTION: ${questionText}`,
    `EXPECTED POINTS: minimum ${expectedPoints.minimum} / target ${expectedPoints.target} / excellent ${expectedPoints.excellent}`,
    'USER ANSWER:',
    answerText
  ].join('\n');

  try {
    return await callGemini(`${systemPrompt}\n\n${userMessage}`);
  } catch (error) {
    if (error.code === 'AI_SCHEMA_ERROR') {
      return { response_type: 'schema_invalid', total_points: 0, skills_detected: [] };
    }
    throw error;
  }
}

async function callResultsPrompt(session) {
  if (isMockMode()) return buildMockResults(session);
  console.log('[AI] AI_MODE=real -> calling Gemini results');

  const scoresObj = { ...session.microSkillScores };
  const systemPrompt = readPromptFile(resultsPromptPath);
  const userMessage = [
    `QUESTIONS ANSWERED: ${session.counters.questionsAnswered}`,
    `QUESTIONS SKIPPED: ${session.counters.questionsSkipped}`,
    `INVALID ANSWERS: ${session.counters.invalidAnswers}`,
    `TOTAL POINTS: ${session.points.total}`,
    `CHECKPOINT REACHED: ${session.checkpoints.reached}`,
    'MICRO-SKILL SCORES:',
    JSON.stringify(scoresObj, null, 2)
  ].join('\n');

  const aiResponse = await callGemini(`${systemPrompt}\n\n${userMessage}`);
  const validation = validateResultsResponse(aiResponse);
  if (!validation.valid) {
    console.error('[AI] Results schema invalid', { reason: validation.reason, provider: 'gemini', model: MODEL_NAME });
    const error = new Error('AI schema error');
    error.code = 'AI_SCHEMA_ERROR';
    error.details = validation.reason;
    throw error;
  }
  return aiResponse;
}

function getRuntimeStatus() {
  const mode = isMockMode() ? 'mock' : 'real';
  return {
    mode,
    provider: 'gemini',
    keyLoaded: Boolean(API_KEY),
    model: MODEL_NAME,
    promptFilesOk: fs.existsSync(scoringPromptPath) && fs.existsSync(resultsPromptPath)
  };
}

module.exports = { initialize, callScoringPrompt, callResultsPrompt, isMockMode, validateResultsResponse, getRuntimeStatus, shouldEnableForceTokens };

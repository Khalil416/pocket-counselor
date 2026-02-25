const fs = require('fs');
const path = require('path');

const scoringPromptPath = path.join(__dirname, '..', 'prompts', 'scoring.txt');
const resultsPromptPath = path.join(__dirname, '..', 'prompts', 'results.txt');
const RESULT_PROFILE_QUALITY = new Set(['Basic', 'Good', 'Very Detailed', 'Maximum']);
const RESULT_CATEGORY_LABELS = new Set(['Weak', 'Basic', 'Good', 'Strong', 'Exceptional']);

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

function readPromptFile(promptPath) {
  const candidates = [
    promptPath,
    path.join(process.cwd(), 'prompts', path.basename(promptPath))
  ];

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
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
  if (!aiResponse || typeof aiResponse !== 'object' || Array.isArray(aiResponse)) return false;
  if (!RESULT_PROFILE_QUALITY.has(aiResponse.profile_quality)) return false;
  if (typeof aiResponse.overall_summary !== 'string') return false;
  if (!Array.isArray(aiResponse.categories) || !Array.isArray(aiResponse.strongest_areas) || !Array.isArray(aiResponse.growth_areas)) return false;

  for (const category of aiResponse.categories) {
    if (!category || typeof category !== 'object' || Array.isArray(category)) return false;
    if (typeof category.name !== 'string' || typeof category.explanation !== 'string') return false;
    if (!Number.isInteger(category.score) || category.score < 0 || category.score > 100) return false;
    if (!RESULT_CATEGORY_LABELS.has(category.label)) return false;
  }

  for (const area of [...aiResponse.strongest_areas, ...aiResponse.growth_areas]) {
    if (!area || typeof area !== 'object' || Array.isArray(area)) return false;
    if (typeof area.skill_name !== 'string' || typeof area.reason !== 'string') return false;
  }

  return true;
}

async function callScoringPrompt(questionText, answerText, expectedPoints) {
  if (isMockMode()) return buildMockScoring(answerText);

  let prompt = readPromptFile(scoringPromptPath);
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
  let prompt = readPromptFile(resultsPromptPath);
  prompt = prompt.replace('{questions_answered}', String(session.counters.questionsAnswered));
  prompt = prompt.replace('{questions_skipped}', String(session.counters.questionsSkipped));
  prompt = prompt.replace('{invalid_answers}', String(session.counters.invalidAnswers));
  prompt = prompt.replace('{total_points}', String(session.points.total));
  prompt = prompt.replace('{checkpoint_number}', String(session.checkpoints.reached));
  prompt = prompt.replace('{micro_skill_scores}', JSON.stringify(scoresObj, null, 2));
  const aiResponse = await callGemini(prompt);
  if (!validateResultsResponse(aiResponse)) throw new Error('AI schema error');
  return aiResponse;
}

module.exports = { initialize, callScoringPrompt, callResultsPrompt, isMockMode, validateResultsResponse };

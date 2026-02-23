/**
 * AI API MODULE
 * Loads prompts from /prompts/*.txt, fills placeholders, calls Gemini API.
 * Two functions: callScoringPrompt (PROMPT A) and callResultsPrompt (PROMPT B).
 */

const fs = require('fs');
const path = require('path');

// Load prompt templates once at startup
const scoringPromptPath = path.join(__dirname, '..', 'prompts', 'scoring.txt');
const resultsPromptPath = path.join(__dirname, '..', 'prompts', 'results.txt');
const SCORING_PROMPT = fs.readFileSync(scoringPromptPath, 'utf-8');
const RESULTS_PROMPT = fs.readFileSync(resultsPromptPath, 'utf-8');

// API configuration
const GEMINI_API_BASE = 'https://generativelanguage.googleapis.com/v1beta';
let API_KEY = '';
let MODEL_NAME = 'gemini-1.5-flash';

/**
 * Initialize the API module with config
 */
function initialize(config = {}) {
    if (config.apiKey) API_KEY = config.apiKey;
    if (config.model) MODEL_NAME = config.model;
}

/**
 * Call Gemini API with a prompt string
 * Returns parsed JSON from the AI response
 * Retries once on failure
 */
async function callGemini(promptText) {
    if (!API_KEY) {
        throw new Error('Gemini API key not configured. Set GEMINI_API_KEY environment variable.');
    }

    // Split SYSTEM: and USER: sections from the prompt
    const systemMatch = promptText.match(/^SYSTEM:\s*([\s\S]*?)(?=\nUSER:|$)/);
    const userMatch = promptText.match(/USER:[\s\S]*$/);

    const systemText = systemMatch ? systemMatch[1].trim() : '';
    const userText = userMatch ? userMatch[0].replace(/^USER:\s*/, '').trim() : promptText;

    const url = `${GEMINI_API_BASE}/models/${MODEL_NAME}:generateContent?key=${API_KEY}`;

    const requestBody = {
        system_instruction: {
            parts: [{ text: systemText }]
        },
        contents: [
            {
                role: 'user',
                parts: [{ text: userText }]
            }
        ],
        generationConfig: {
            temperature: 0.3,
            responseMimeType: 'application/json'
        }
    };

    console.log(`[AI] Sending request to ${MODEL_NAME}...`);

    // Try up to 2 times (initial + 1 retry)
    for (let attempt = 0; attempt < 2; attempt++) {
        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Gemini API error ${response.status}: ${errorText}`);
            }

            const data = await response.json();
            const textContent = data?.candidates?.[0]?.content?.parts?.[0]?.text;
            if (!textContent) throw new Error('Empty response from Gemini API');

            // Parse JSON — strip markdown code blocks if present
            let jsonText = textContent.trim();
            
            // Remove markdown code blocks
            if (jsonText.includes('```json')) {
                const start = jsonText.indexOf('```json') + 7;
                const end = jsonText.indexOf('```', start);
                if (end !== -1) {
                    jsonText = jsonText.substring(start, end).trim();
                }
            } else if (jsonText.includes('```')) {
                const start = jsonText.indexOf('```') + 3;
                const end = jsonText.indexOf('```', start);
                if (end !== -1) {
                    jsonText = jsonText.substring(start, end).trim();
                } else {
                    jsonText = jsonText.substring(start).trim();
                }
            }
            
            // Try to find JSON object boundaries if response has extra text
            const jsonStart = jsonText.indexOf('{');
            const jsonEnd = jsonText.lastIndexOf('}');
            if (jsonStart !== -1 && jsonEnd !== -1 && jsonEnd > jsonStart) {
                jsonText = jsonText.substring(jsonStart, jsonEnd + 1);
            }

            console.log(`[AI] Response length: ${textContent.length} chars`);
            console.log(`[AI] Extracted JSON length: ${jsonText.length} chars`);
            
            try {
                const parsed = JSON.parse(jsonText.trim());
                console.log(`[AI] Successfully parsed JSON`);
                return parsed;
            } catch (e) {
                console.error('[AI] JSON Parse Error:', e.message);
                console.error('[AI] First 500 chars of response:', textContent.substring(0, 500));
                console.error('[AI] Extracted JSON (first 500 chars):', jsonText.substring(0, 500));
                throw new Error(`AI response was not valid JSON: ${e.message}`);
            }
        } catch (error) {
            console.error(`AI call attempt ${attempt + 1} failed:`, error.message);
            if (attempt === 1) throw error; // second attempt failed, propagate
            await new Promise(resolve => setTimeout(resolve, 1000)); // wait before retry
        }
    }
}

/**
 * PROMPT A — Score a single answer
 * Fills placeholders in scoring.txt and sends to AI
 */
async function callScoringPrompt(questionText, answerText, expectedPoints) {
    let prompt = SCORING_PROMPT;
    prompt = prompt.replace('{question_text}', questionText);
    prompt = prompt.replace('{min}', String(expectedPoints.minimum));
    prompt = prompt.replace('{target}', String(expectedPoints.target));
    prompt = prompt.replace('{excellent}', String(expectedPoints.excellent));
    prompt = prompt.replace('{answer_text}', answerText);

    return await callGemini(prompt);
}

/**
 * PROMPT B — Generate final results
 * Fills placeholders in results.txt and sends to AI
 */
async function callResultsPrompt(session) {
    // Build the micro-skill scores JSON string
    const scoresObj = { ...session.microSkillScores };
    delete scoresObj.INVALID; // don't send INVALID key to results prompt

    let prompt = RESULTS_PROMPT;
    prompt = prompt.replace('{questions_answered}', String(session.counters.questionsAnswered));
    prompt = prompt.replace('{questions_skipped}', String(session.counters.questionsSkipped));
    prompt = prompt.replace('{invalid_answers}', String(session.counters.invalidAnswers));
    prompt = prompt.replace('{total_points}', String(session.points.total));
    prompt = prompt.replace('{checkpoint_number}', String(session.checkpoints.reached));
    prompt = prompt.replace('{micro_skill_scores}', JSON.stringify(scoresObj, null, 2));

    return await callGemini(prompt);
}

module.exports = {
    initialize,
    callScoringPrompt,
    callResultsPrompt
};

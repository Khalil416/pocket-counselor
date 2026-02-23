/**
 * POCKET COUNSELOR — Express Server
 * Main entry point. The code is just a shell:
 * - Show questions
 * - Collect answers
 * - Send to AI via API
 * - Store what AI returns
 * - Check checkpoints
 * - Display results that AI generates
 */

const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');

const {
    createSession, getSession, getNextQuestion,
    skipQuestion, canSkip, finishSession,
    getQuestionById, applyAiScoring, getClientSession
} = require('./session');

const { checkForCheckpoint, getProfileLabel } = require('./checkpoint');
const api = require('./api');

// ─── Config ───────────────────────────────────────────
const PORT = process.env.PORT || 8080;

// Try to read API key from application.properties (existing Java config)
function loadApiKeyFromProperties() {
    try {
        const propsPath = path.join(__dirname, 'src', 'main', 'resources', 'application.properties');
        const content = fs.readFileSync(propsPath, 'utf-8');
        const match = content.match(/gemini\.api\.key=(.+)/);
        const modelMatch = content.match(/gemini\.model=(.+)/);
        return {
            apiKey: match ? match[1].trim() : '',
            model: modelMatch ? modelMatch[1].trim() : 'gemini-2.5-flash'
        };
    } catch {
        return { apiKey: '', model: 'gemini-2.5-flash' };
    }
}

// Initialize AI API
const propsConfig = loadApiKeyFromProperties();
api.initialize({
    apiKey: process.env.GEMINI_API_KEY || propsConfig.apiKey,
    model: process.env.GEMINI_MODEL || propsConfig.model
});

// ─── Express Setup ───────────────────────────────────
const app = express();
app.use(cors());
app.use(express.json());

// Serve frontend static files
app.use(express.static(path.join(__dirname, '..', 'frontend')));

// ─── API Routes ──────────────────────────────────────

/**
 * Health check
 */
app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', message: 'Pocket Counselor API is running' });
});

/**
 * Start a new session — returns session info + first question
 */
app.post('/api/session/start', (req, res) => {
    try {
        const session = createSession();
        const firstQuestion = getNextQuestion(session);

        res.json({
            sessionId: session.sessionId,
            question: firstQuestion,
            session: getClientSession(session),
            canSkip: canSkip(session)
        });
    } catch (error) {
        console.error('Error starting session:', error);
        res.status(500).json({ error: 'Failed to start session' });
    }
});

/**
 * Submit an answer — NON-BLOCKING
 * Immediately returns the next question.
 * AI scoring + checkpoint detection run in the background.
 */
app.post('/api/session/:id/answer', async (req, res) => {
    const session = getSession(req.params.id);
    if (!session) return res.status(404).json({ error: 'Session not found' });

    const { questionId, answer } = req.body;
    if (!questionId || !answer) {
        return res.status(400).json({ error: 'questionId and answer are required' });
    }

    // Validate minimum length
    if (answer.trim().length < 10) {
        return res.status(400).json({
            error: 'answer_too_short',
            message: 'Please share a bit more — a few sentences work best.'
        });
    }

    const questionData = getQuestionById(questionId);
    if (!questionData) {
        return res.status(400).json({ error: 'Invalid question ID' });
    }

    // Fire AI scoring in the background — do NOT block the user.
    // Checkpoints and warnings are stored on the session and picked up by /status polling.
    FireScoring(session, questionData, answer.trim());

    // Immediately get next question
    const nextQuestion = getNextQuestion(session);
    const isFinished = !nextQuestion && session.counters.questionsShown >= 35;

    if (isFinished) {
        finishSession(session);
    }

    res.json({
        nextQuestion,
        session: getClientSession(session),
        canSkip: canSkip(session),
        finished: isFinished,
        checkpoint: null,
        warning: null
    });
});

/**
 * AI scoring — runs synchronously now
 */
async function FireScoring(session, questionData, answerText) {
    console.log(`[Server] Scoring Q${questionData.id}...`);
    try {
        const aiResponse = await api.callScoringPrompt(
            questionData.text,
            answerText,
            questionData.expected_points
        );

        // Apply scoring to session
        applyAiScoring(session, questionData, answerText, aiResponse);

        const result = { checkpoint: null, warning: null };

        // Check for checkpoint (synchronous — result returned with this answer)
        const checkpoint = checkForCheckpoint(session);
        if (checkpoint) {
            // Persist to session so /status polling can also pick it up
            session._pendingCheckpoint = checkpoint;
            result.checkpoint = checkpoint;
        }

        // Check invalid answer warnings
        if (session.counters.invalidAnswers >= 7) {
            result.warning = 'Too many unclear answers detected. Results may not be reliable.';
        } else if (session.counters.invalidAnswers >= 4) {
            result.warning = 'Some of your answers were unclear — this may affect accuracy.';
        }

        if (result.warning) {
            session._pendingWarning = result.warning;
        }

        return result;

    } catch (error) {
        console.error('Scoring failed:', error.message);
        return null; // continue quiz even if AI fails
    }
}

/**
 * Poll for scoring status — frontend calls this to check
 * if a checkpoint was reached or warnings need showing
 */
app.get('/api/session/:id/status', (req, res) => {
    const session = getSession(req.params.id);
    if (!session) return res.status(404).json({ error: 'Session not found' });

    const checkpoint = session._pendingCheckpoint || null;
    const warning = session._pendingWarning || null;

    // Clear pending state after reading
    if (checkpoint) delete session._pendingCheckpoint;
    if (warning) delete session._pendingWarning;

    res.json({
        checkpoint,
        warning,
        session: getClientSession(session)
    });
});

/**
 * Skip a question
 */
app.post('/api/session/:id/skip', (req, res) => {
    const session = getSession(req.params.id);
    if (!session) return res.status(404).json({ error: 'Session not found' });

    const { questionId } = req.body;
    if (!questionId) return res.status(400).json({ error: 'questionId is required' });

    if (!canSkip(session)) {
        return res.status(400).json({ error: 'Maximum skips reached' });
    }

    const maxSkipsReached = skipQuestion(session, questionId);
    const nextQuestion = getNextQuestion(session);
    const isFinished = !nextQuestion;

    if (isFinished) finishSession(session);

    res.json({
        skipped: true,
        maxSkipsReached,
        nextQuestion,
        session: getClientSession(session),
        canSkip: canSkip(session),
        finished: isFinished
    });
});

/**
 * Get final results — calls PROMPT B
 * AI generates everything. Code just passes it through.
 */
app.get('/api/session/:id/results', async (req, res) => {
    const session = getSession(req.params.id);
    if (!session) return res.status(404).json({ error: 'Session not found' });

    finishSession(session);

    try {
        console.log('[Results] Generating final profile...');

        // Call PROMPT B — AI decides categories, scores, explanations.
        // Increased timeout to 90 seconds and add retry logic
        let aiResults = null;
        let lastError = null;
        
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    console.log(`[Results] Retry attempt ${attempt + 1}/3...`);
                    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2s between retries
                }
                
                aiResults = await Promise.race([
                    api.callResultsPrompt(session),
                    new Promise((_, reject) =>
                        setTimeout(() => reject(new Error('Results generation timed out after 90 seconds')), 90000)
                    )
                ]);
                
                // Validate that we got a valid response
                if (aiResults && typeof aiResults === 'object') {
                    console.log('[Results] AI profile generated successfully.');
                    break;
                } else {
                    throw new Error('Invalid response format');
                }
            } catch (error) {
                lastError = error;
                console.error(`[Results] Attempt ${attempt + 1} failed:`, error.message);
                if (attempt === 2) throw error; // Last attempt failed
            }
        }

        // Return AI response directly — code does not touch it
        res.json(aiResults);
    } catch (error) {
        console.error('Results generation failed:', error);
        // Fallback — return basic info so user isn't stuck
        res.json({
            profile_quality: getProfileLabel(session.checkpoints.reached),
            overall_summary: 'We were unable to generate a detailed analysis at this time. Please try again.',
            categories: [],
            strongest_areas: [],
            needs_attention: [],
            data_quality_note: 'Results generation encountered an error.'
        });
    }
});

// ─── Start Server ──────────────────────────────────
app.listen(PORT, () => {
    console.log(`\n✅ Pocket Counselor server running on http://localhost:${PORT}`);
    console.log(`📋 API: http://localhost:${PORT}/api/health`);
    console.log(`🌐 Frontend: http://localhost:${PORT}\n`);
});

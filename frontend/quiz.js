/**
 * QUIZ UI LOGIC
 * Non-blocking: submits answer, immediately shows next question.
 * Polls /state to detect checkpoints and warnings.
 */

// ─── State ───────────────────────────────────────────
let sessionId = null;
let currentQuestion = null;
let canSkipCurrent = true;
let isProcessing = false;
let statusPollInterval = null;
let pendingNextQuestion = null; // holds the next question when a checkpoint is shown
let cp1NotReachedHintShown = false;
let forceCheckpoint15Shown = false;
let submittedAnswersCount = 0;
let resultsCheckpointScheduled = false;

// DOM references
let questionText, answerInput, charCount, charHint;
let questionCounter, nextBtn, skipBtn, skipCounter, skipCountEl;
let answerWarning;

document.addEventListener('DOMContentLoaded', () => {
    questionText = document.getElementById('questionText');
    answerInput = document.getElementById('answerInput');
    charCount = document.getElementById('charCount');
    charHint = document.getElementById('charHint');
    questionCounter = document.getElementById('questionCounter');
    nextBtn = document.getElementById('nextBtn');
    skipBtn = document.getElementById('skipBtn');
    skipCounter = document.getElementById('skipCounter');
    skipCountEl = document.getElementById('skipCount');
    answerWarning = document.getElementById('answerWarning');

    // Character count
    answerInput.addEventListener('input', () => {
        const len = answerInput.value.length;
        charCount.textContent = len;
        charHint.classList.toggle('warning', len > 0 && len < 10);
    });

    // Ctrl+Enter shortcut
    answerInput.addEventListener('keydown', (e) => {
        if (e.ctrlKey && e.key === 'Enter') handleNext();
    });
});

// ─── Screen Management ──────────────────────────────
function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach((s) => {
        s.classList.remove('active');
        s.style.display = 'none';
    });

    const target = document.getElementById(screenId);
    if (target) {
        target.classList.add('active');
        target.style.display = 'flex';
    }
}

function showLoading(message = 'Preparing your assessment...') {
    const overlay = document.getElementById('loadingOverlay');
    overlay.querySelector('.loading-text').textContent = message;
    overlay.style.display = 'flex';
}

function hideLoading() {
    document.getElementById('loadingOverlay').style.display = 'none';
}

// ─── Start Quiz ─────────────────────────────────────
async function startQuiz() {
    showLoading('Starting your assessment...');
    forceCheckpoint15Shown = false;
    cp1NotReachedHintShown = false;
    pendingNextQuestion = null;
    submittedAnswersCount = 0;
    resultsCheckpointScheduled = false;

    try {
        const res = await fetch('/api/session/start', { method: 'POST' });
        if (!res.ok) throw new Error('Failed to start session');

        const data = await res.json();
        sessionId = data.sessionId;
        currentQuestion = data.question;
        canSkipCurrent = data.canSkip;

        hideLoading();
        showScreen('quizScreen');
        renderQuestion();

        // Start polling for checkpoint/warning updates
        startStatusPolling();
    } catch (error) {
        hideLoading();
        console.error('Error starting quiz:', error);
        showToast('Failed to start the assessment. Please try again.', 'error');
    }
}

// ─── Status Polling ─────────────────────────────────
function startStatusPolling() {
    if (statusPollInterval) clearInterval(statusPollInterval);
    statusPollInterval = setInterval(pollStatus, 3000);
}

function stopStatusPolling() {
    if (statusPollInterval) {
        clearInterval(statusPollInterval);
        statusPollInterval = null;
    }
}

async function pollStatus() {
    if (!sessionId) return;

    try {
        const res = await fetch(`/api/session/${sessionId}/state`);
        if (!res.ok) return;

        const data = await res.json();

        // Show warning if any
        if (data.warning) {
            showToast(data.warning === 'invalid_hard' ? 'Geçersiz cevap sayısı yüksek. Sonuç kalitesi düşebilir.' : 'Bazı cevaplar geçersiz algılandı.', 'warning');
        }

        // Handle checkpoint
        if (data.checkpoint_reached_now && data.checkpoint) {
            if (forceCheckpoint15Shown && data.checkpoint.level === 1) {
                return;
            }
            stopStatusPolling();
            if (data.checkpoint.autoShow) {
                showResults();
            } else {
                showCheckpointModal(data.checkpoint);
            }
        }
    } catch (error) {
        // Silent fail — polling is best-effort
    }
}

async function waitUntilResultsReady(maxAttempts = 30, intervalMs = 200) {
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
        const res = await fetch(`/api/session/${sessionId}/state`);
        if (!res.ok) {
            await new Promise((resolve) => setTimeout(resolve, intervalMs));
            continue;
        }

        const state = await res.json();
        if (state?.scoring?.results_ready) {
            return state;
        }

        await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    return null;
}

async function maybeShowResultsReadyCheckpoint(data) {
    if (resultsCheckpointScheduled || submittedAnswersCount < 15) return;
    resultsCheckpointScheduled = true;

    try {
        const readyState = await waitUntilResultsReady();
        if (!readyState) {
            console.warn('Results-ready checkpoint was not reached in time', {
                submittedAnswersCount,
                sessionId
            });
            return;
        }

        forceCheckpoint15Shown = true;
        cp1NotReachedHintShown = true;
        stopStatusPolling();

        setTimeout(() => {
            showCheckpointModal({
                level: 0,
                label: 'Results are ready',
                questionsAnswered: readyState.session?.counters?.questionsAnswered ?? 15,
                totalPoints: readyState.session?.points?.total ?? data.session?.points?.total ?? 0,
                autoShow: false
            });
        }, 500);
    } catch (error) {
        console.error('Failed to auto-show results checkpoint', error);
    }
}

// ─── Render Question ────────────────────────────────
function renderQuestion() {
    if (!currentQuestion) return;

    const card = document.getElementById('questionCard');
    card.style.animation = 'none';
    card.offsetHeight;
    card.style.animation = 'slideUp 0.4s ease';

    questionText.textContent = currentQuestion.text;
    questionCounter.textContent = `Question ${currentQuestion.questionNumber}`;

    answerInput.value = '';
    charCount.textContent = '0';
    charHint.classList.remove('warning');
    answerInput.focus();

    skipBtn.style.display = canSkipCurrent ? '' : 'none';
    skipBtn.disabled = false;
    answerWarning.style.display = 'none';

    isProcessing = false;
    nextBtn.disabled = false;
}

// ─── Handle Next (Submit Answer — NON-BLOCKING) ────
async function handleNext() {
    if (isProcessing) return;

    const answer = answerInput.value.trim();

    if (answer.length < 10) {
        answerWarning.textContent = 'Please share a bit more — a few sentences work best.';
        answerWarning.style.display = 'block';
        answerInput.focus();
        return;
    }

    answerWarning.style.display = 'none';
    isProcessing = true;
    nextBtn.disabled = true;
    skipBtn.disabled = true;

    try {
        const res = await fetch(`/api/session/${sessionId}/answer`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ questionId: currentQuestion.id, answerText: answer })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            if (errData.error === 'answer_too_short') {
                answerWarning.textContent = errData.message;
                answerWarning.style.display = 'block';
                isProcessing = false;
                nextBtn.disabled = false;
                skipBtn.disabled = false;
                return;
            }
            console.error('Submit answer failed', {
                status: res.status,
                endpoint: `/api/session/${sessionId}/answer`,
                payload: { questionId: currentQuestion.id, answerText: answer },
                response: errData
            });
            throw new Error(errData.error || 'Failed to submit answer');
        }

        submittedAnswersCount += 1;
        const data = await res.json();
        canSkipCurrent = data.canSkip;

        // Update skip counter display
        if (data.session && data.session.counters.questionsSkipped > 0) {
            skipCounter.style.display = 'block';
            skipCountEl.textContent = data.session.counters.questionsSkipped;
        }

        // Handle Finish (no more questions)
        if (data.finished || !data.nextQuestion) {
            stopStatusPolling();
            showLoading('Finishing up...');
            showResults();
            return;
        }

        // Show next question immediately (non-blocking scoring)
        currentQuestion = data.nextQuestion;
        renderQuestion();
        void maybeShowResultsReadyCheckpoint(data);

    } catch (error) {
        console.error('Error submitting answer:', error);
        isProcessing = false;
        nextBtn.disabled = false;
        skipBtn.disabled = false;
        showToast('Something went wrong. Please try again.', 'error');
    }
}

// ─── Handle Skip ────────────────────────────────────
async function handleSkip() {
    if (isProcessing || !canSkipCurrent) return;

    isProcessing = true;
    skipBtn.disabled = true;
 
    try {
        const res = await fetch(`/api/session/${sessionId}/skip`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ questionId: currentQuestion.id })
        });

        if (!res.ok) throw new Error('Failed to skip');

        const data = await res.json();
        canSkipCurrent = data.canSkip;

        if (data.session) {
            skipCounter.style.display = 'block';
            skipCountEl.textContent = data.session.counters.questionsSkipped;
        }

        if (data.maxSkipsReached) {
            showToast('Maximum skips reached. Please answer the remaining questions.', 'warning');
        }

        if (data.finished || !data.nextQuestion) {
            stopStatusPolling();
            showResults();
            return;
        }

        currentQuestion = data.nextQuestion;
        renderQuestion();

    } catch (error) {
        console.error('Error skipping:', error);
        isProcessing = false;
        skipBtn.disabled = false;
        showToast('Failed to skip. Please try again.', 'error');
    }
}

// ─── Checkpoint Modal ───────────────────────────────
function showCheckpointModal(checkpoint) {
    const modal = document.getElementById('checkpointModal');
    const badgeText = checkpoint.level === 0
        ? checkpoint.label
        : `${checkpoint.label} Profile`;
    document.getElementById('modalBadge').textContent = badgeText;

    let infoText = `Questions Answered: ${checkpoint.questionsAnswered}`;
    if (typeof checkpoint.totalPoints === 'number') {
        infoText += ` • Total Points: ${checkpoint.totalPoints}`;
    }
    document.getElementById('modalInfo').textContent = infoText;
    modal.style.display = 'flex';
}

function continueQuiz() {
    document.getElementById('checkpointModal').style.display = 'none';

    // If we already have the next question from the last answer,
    // use it now; otherwise just resume polling and keep going.
    if (pendingNextQuestion) {
        currentQuestion = pendingNextQuestion;
        pendingNextQuestion = null;
        renderQuestion();
    }

    startStatusPolling(); // resume polling
}

// ─── Show Results ───────────────────────────────────
async function showResults() {
    // Hide checkpoint modal if it's open
    const checkpointModal = document.getElementById('checkpointModal');
    if (checkpointModal) {
        checkpointModal.style.display = 'none';
    }
    
    stopStatusPolling();
    showLoading('Generating your skill profile...');

    try {
        const endpoint = `/api/session/${sessionId}/results`;
        const res = await fetch(endpoint);
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            console.error('Results request failed', {
                status: res.status,
                endpoint,
                response: data
            });
            throw new Error(data?.error?.code || 'Failed to get results');
        }

        // Hide loading first so it doesn't block the view
        hideLoading();
        
        // Show results screen and force visibility
        showScreen('resultsScreen');
        const resultsScreen = document.getElementById('resultsScreen');
        if (resultsScreen) {
            resultsScreen.style.display = 'flex';
            resultsScreen.classList.add('active');
            resultsScreen.style.visibility = 'visible';
            resultsScreen.style.opacity = '1';
        }
        
        // Render after the screen is visible (requestAnimationFrame ensures paint)
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                renderResults(data);
                // Scroll results into view
                const container = document.querySelector('.results-container');
                if (container) container.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        });

    } catch (error) {
        hideLoading();
        console.error('Error getting results:', error);
        showToast('Failed to load results. Please try again.', 'error');
    }
}


async function showAnswersScreen() {
    if (!sessionId) return;

    showLoading('Loading your answers...');

    try {
        const res = await fetch(`/api/session/${sessionId}/answers`);
        if (!res.ok) throw new Error('Failed to get answers');

        const data = await res.json();
        hideLoading();
        showScreen('answersScreen');

        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                renderAnswers(data);
                const container = document.querySelector('#answersScreen .results-container');
                if (container) container.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        });
    } catch (error) {
        hideLoading();
        console.error('Error loading answers:', error);
        showToast('Failed to load answers. Please try again.', 'error');
    }
}

// ─── Restart Quiz ───────────────────────────────────
function restartQuiz() {
    sessionId = null;
    currentQuestion = null;
    forceCheckpoint15Shown = false;
    cp1NotReachedHintShown = false;
    pendingNextQuestion = null;
    submittedAnswersCount = 0;
    resultsCheckpointScheduled = false;
    stopStatusPolling();
    showScreen('welcomeScreen');
}

// ─── Toast Notifications ────────────────────────────
function showToast(message, type = 'info') {
    document.querySelectorAll('.toast').forEach(t => t.remove());

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `<div class="toast-text">${message}</div>`;
    document.body.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        toast.style.transition = '0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 5000);
}

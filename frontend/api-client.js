(function initApiClient(globalScope) {
    const DEFAULTS = {
        timeoutMs: 5000,
        maxAttempts: 4,
        maxTotalWaitMs: 10000,
        baseDelayMs: 250,
        maxDelayMs: 2000,
        maxBodyPreviewChars: 500,
        maxAnswerChars: 4000,
        minPollIntervalMs: 300,
        maxPollDurationMs: 30000,
        pollBackoffMultiplier: 1.5,
        maxPollIntervalMs: 2500
    };

    class ApiClientError extends Error {
        constructor(message, details = {}) {
            super(message);
            this.name = this.constructor.name;
            this.details = details;
        }
    }

    class NetworkError extends ApiClientError {}
    class RateLimitError extends ApiClientError {}
    class ServerError extends ApiClientError {}
    class ParseError extends ApiClientError {}
    class SchemaError extends ApiClientError {}
    class FlowError extends ApiClientError {}

    class HttpStatusError extends ApiClientError {
        constructor(message, details = {}) {
            super(message, details);
            this.status = details.status;
            this.responseBody = details.responseBody;
            this.responseHeaders = details.responseHeaders;
        }
    }

    function createId() {
        if (globalScope.crypto && typeof globalScope.crypto.randomUUID === 'function') {
            return globalScope.crypto.randomUUID();
        }
        return `pc-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    function sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
    }

    function safeJsonPreview(value, maxChars) {
        try {
            const str = typeof value === 'string' ? value : JSON.stringify(value);
            if (!str) return '';
            return str.length > maxChars ? `${str.slice(0, maxChars)}…[trimmed ${str.length - maxChars} chars]` : str;
        } catch {
            return '[unserializable]';
        }
    }

    function sanitizeBodyForLog(body) {
        if (!body || typeof body !== 'object') return body;
        const clone = { ...body };
        if (typeof clone.answerText === 'string') {
            clone.answerText = `[redacted length=${clone.answerText.length}]`;
        }
        return clone;
    }

    function parseRetryAfterMs(retryAfterHeader) {
        if (!retryAfterHeader) return null;
        const seconds = Number(retryAfterHeader);
        if (!Number.isNaN(seconds) && seconds >= 0) {
            return seconds * 1000;
        }
        const timestampMs = Date.parse(retryAfterHeader);
        if (!Number.isNaN(timestampMs)) {
            return Math.max(0, timestampMs - Date.now());
        }
        return null;
    }

    function computeBackoffMs(attempt, config) {
        const exp = Math.min(config.maxDelayMs, config.baseDelayMs * (2 ** (attempt - 1)));
        const jitter = Math.floor(Math.random() * Math.max(1, Math.floor(exp * 0.25)));
        return exp + jitter;
    }

    function isRetryableStatus(status) {
        return status === 429 || (status >= 500 && status <= 599);
    }

    function validateStartResponse(data) {
        if (!data || typeof data !== 'object') throw new SchemaError('Invalid /start response shape');
        if (typeof data.sessionId !== 'string' || !data.sessionId) throw new SchemaError('Missing sessionId in /start response');
        if (!data.question || typeof data.question !== 'object' || typeof data.question.id === 'undefined') {
            throw new SchemaError('Missing question.id in /start response');
        }
        return data;
    }

    function validateStateResponse(data) {
        if (!data || typeof data !== 'object') throw new SchemaError('Invalid /state response shape');
        return data;
    }

    function validateAnswerResponse(data) {
        if (!data || typeof data !== 'object') throw new SchemaError('Invalid /answer response shape');
        if (!data.finished && data.nextQuestion && typeof data.nextQuestion !== 'object') {
            throw new SchemaError('Invalid nextQuestion in /answer response');
        }
        return data;
    }

    class PocketCounselorApiClient {
        constructor(config = {}) {
            this.config = { ...DEFAULTS, ...config };
            this.sessionState = new Map();
        }

        updateSessionQuestion(sessionId, questionId) {
            const previous = this.sessionState.get(sessionId) || { currentQuestionId: null, answeredQuestionIds: new Set() };
            previous.currentQuestionId = questionId;
            this.sessionState.set(sessionId, previous);
        }

        markAnswered(sessionId, questionId) {
            const previous = this.sessionState.get(sessionId) || { currentQuestionId: null, answeredQuestionIds: new Set() };
            previous.answeredQuestionIds.add(questionId);
            this.sessionState.set(sessionId, previous);
        }

        getSessionFlow(sessionId) {
            return this.sessionState.get(sessionId) || { currentQuestionId: null, answeredQuestionIds: new Set() };
        }

        async request(endpoint, options = {}) {
            const cfg = this.config;
            const method = (options.method || 'GET').toUpperCase();
            const correlationId = options.correlationId || createId();
            const headers = new Headers(options.headers || {});
            headers.set('X-Correlation-Id', correlationId);

            if (method === 'POST' && !headers.has('Idempotency-Key')) {
                headers.set('Idempotency-Key', options.idempotencyKey || createId());
            }

            const body = options.body;
            const requestPreview = safeJsonPreview(sanitizeBodyForLog(body), cfg.maxBodyPreviewChars);
            const startedAt = Date.now();

            let lastError = null;
            for (let attempt = 1; attempt <= cfg.maxAttempts; attempt += 1) {
                const controller = new AbortController();
                const timeoutHandle = setTimeout(() => controller.abort(), cfg.timeoutMs);

                try {
                    const fetchOptions = {
                        method,
                        headers,
                        signal: controller.signal
                    };

                    if (body !== undefined) {
                        fetchOptions.body = typeof body === 'string' ? body : JSON.stringify(body);
                        if (!headers.has('Content-Type')) {
                            headers.set('Content-Type', 'application/json');
                        }
                    }

                    const response = await fetch(endpoint, fetchOptions);
                    clearTimeout(timeoutHandle);

                    const text = await response.text();
                    let parsed;
                    try {
                        parsed = text ? JSON.parse(text) : {};
                    } catch (error) {
                        const preview = text.length > cfg.maxBodyPreviewChars ? `${text.slice(0, cfg.maxBodyPreviewChars)}…[trimmed ${text.length - cfg.maxBodyPreviewChars} chars]` : text;
                        console.error('[api-client] json_parse_failed', { correlationId, endpoint, status: response.status, rawBodyPreview: preview });
                        throw new ParseError('Failed to parse JSON response', {
                            endpoint,
                            status: response.status,
                            correlationId,
                            rawBodyPreview: preview,
                            cause: error
                        });
                    }

                    const responsePreview = safeJsonPreview(sanitizeBodyForLog(parsed), cfg.maxBodyPreviewChars);
                    console.info('[api-client] response', {
                        correlationId,
                        endpoint,
                        method,
                        status: response.status,
                        requestPreview,
                        responsePreview
                    });

                    if (!response.ok) {
                        const error = new HttpStatusError(`Request failed with status ${response.status}`, {
                            endpoint,
                            status: response.status,
                            responseBody: parsed,
                            responseHeaders: Object.fromEntries(response.headers.entries()),
                            correlationId
                        });

                        if (isRetryableStatus(response.status) && attempt < cfg.maxAttempts) {
                            const elapsed = Date.now() - startedAt;
                            const retryAfterMs = parseRetryAfterMs(response.headers.get('Retry-After'));
                            const delayMs = retryAfterMs ?? computeBackoffMs(attempt, cfg);
                            if (elapsed + delayMs <= cfg.maxTotalWaitMs) {
                                await sleep(delayMs);
                                continue;
                            }
                        }

                        if (response.status === 429) throw new RateLimitError('Rate limited by server', error.details);
                        if (response.status >= 500) throw new ServerError('Server error', error.details);
                        throw error;
                    }

                    return { data: parsed, status: response.status, headers: response.headers, correlationId };
                } catch (error) {
                    clearTimeout(timeoutHandle);
                    if (error instanceof ParseError || error instanceof RateLimitError || error instanceof ServerError || error instanceof HttpStatusError) {
                        throw error;
                    }

                    const isAbort = error && error.name === 'AbortError';
                    const networkError = new NetworkError(isAbort ? 'Request timed out' : 'Network failure', {
                        endpoint,
                        method,
                        attempt,
                        correlationId,
                        cause: error
                    });
                    lastError = networkError;

                    if (attempt >= cfg.maxAttempts) {
                        throw networkError;
                    }

                    const elapsed = Date.now() - startedAt;
                    const delayMs = computeBackoffMs(attempt, cfg);
                    if (elapsed + delayMs > cfg.maxTotalWaitMs) {
                        throw networkError;
                    }
                    await sleep(delayMs);
                }
            }

            throw lastError || new NetworkError('Request failed after retries', { endpoint, method });
        }

        async startSession() {
            const response = await this.request('/api/session/start', { method: 'POST' });
            const data = validateStartResponse(response.data);
            this.updateSessionQuestion(data.sessionId, data.question.id);
            return data;
        }

        async getSessionState(sessionId) {
            const response = await this.request(`/api/session/${sessionId}/state`);
            const data = validateStateResponse(response.data);
            if (data?.session?.currentQuestion?.id !== undefined && data?.session?.currentQuestion?.id !== null) {
                this.updateSessionQuestion(sessionId, data.session.currentQuestion.id);
            }
            return data;
        }

        normalizeAnswerText(answerText, maxChars = this.config.maxAnswerChars) {
            if (typeof answerText !== 'string') {
                throw new SchemaError('answerText must be a string');
            }
            if (answerText.length <= maxChars) return answerText;
            const suffix = '…[trimmed]';
            const allowed = Math.max(0, maxChars - suffix.length);
            return `${answerText.slice(0, allowed)}${suffix}`;
        }

        async submitAnswer(sessionId, payload) {
            const flow = this.getSessionFlow(sessionId);
            if (!payload || typeof payload.questionId === 'undefined') {
                throw new FlowError('questionId is required for submitAnswer');
            }

            if (flow.answeredQuestionIds.has(payload.questionId)) {
                throw new FlowError('Question already answered; refusing duplicate submission', { questionId: payload.questionId });
            }

            if (flow.currentQuestionId !== null && flow.currentQuestionId !== payload.questionId) {
                const state = await this.getSessionState(sessionId);
                const serverQuestionId = state?.session?.currentQuestion?.id;
                if (serverQuestionId === payload.questionId) {
                    this.updateSessionQuestion(sessionId, payload.questionId);
                } else {
                    throw new FlowError('Question mismatch; refusing submission until flow is reconciled', {
                        expectedQuestionId: flow.currentQuestionId,
                        receivedQuestionId: payload.questionId,
                        serverQuestionId
                    });
                }
            }

            let normalizedAnswer = this.normalizeAnswerText(payload.answerText);
            console.info('[api-client] submit_answer', {
                endpoint: `/api/session/${sessionId}/answer`,
                questionId: payload.questionId,
                answerTextLength: payload.answerText.length,
                normalizedAnswerTextLength: normalizedAnswer.length
            });

            const submit = async (answerText) => {
                const response = await this.request(`/api/session/${sessionId}/answer`, {
                    method: 'POST',
                    body: { questionId: payload.questionId, answerText }
                });
                return validateAnswerResponse(response.data);
            };

            try {
                const data = await submit(normalizedAnswer);
                this.markAnswered(sessionId, payload.questionId);
                if (data.nextQuestion?.id !== undefined) {
                    this.updateSessionQuestion(sessionId, data.nextQuestion.id);
                } else {
                    this.updateSessionQuestion(sessionId, null);
                }
                return data;
            } catch (error) {
                const isSizeError = error instanceof HttpStatusError && (error.status === 413 || error.status === 400);
                const sizeMessage = String(error?.responseBody?.error || error?.responseBody?.message || '').toLowerCase();
                if (!isSizeError || !sizeMessage.includes('size')) {
                    throw error;
                }

                const baseLimit = Math.min(this.config.maxAnswerChars, normalizedAnswer.length);
                const reducedLimit = Math.max(16, Math.min(baseLimit - 1, Math.floor(baseLimit * 0.7)));
                normalizedAnswer = this.normalizeAnswerText(payload.answerText, reducedLimit);
                const data = await submit(normalizedAnswer);
                this.markAnswered(sessionId, payload.questionId);
                if (data.nextQuestion?.id !== undefined) {
                    this.updateSessionQuestion(sessionId, data.nextQuestion.id);
                } else {
                    this.updateSessionQuestion(sessionId, null);
                }
                return data;
            }
        }

        async waitForCheckpointOrResults(sessionId, options = {}) {
            const minIntervalMs = options.minIntervalMs ?? this.config.minPollIntervalMs;
            const maxDurationMs = options.maxDurationMs ?? this.config.maxPollDurationMs;
            const startedAt = Date.now();
            let intervalMs = minIntervalMs;
            let lastFingerprint = null;

            while (Date.now() - startedAt <= maxDurationMs) {
                const state = await this.getSessionState(sessionId);
                const shouldStop = Boolean(
                    state?.scoring?.results_ready ||
                    state?.checkpoint_reached_now ||
                    state?.warning
                );

                if (shouldStop) {
                    return state;
                }

                const fingerprint = JSON.stringify({
                    checkpoint: state?.current_checkpoint,
                    warning: state?.warning,
                    ready: state?.scoring?.results_ready,
                    currentQuestionId: state?.session?.currentQuestion?.id
                });

                if (fingerprint === lastFingerprint) {
                    intervalMs = Math.min(this.config.maxPollIntervalMs, Math.floor(intervalMs * this.config.pollBackoffMultiplier));
                } else {
                    intervalMs = minIntervalMs;
                    lastFingerprint = fingerprint;
                }

                await sleep(intervalMs);
            }

            throw new FlowError('Polling timed out before checkpoint/results condition was met', {
                sessionId,
                maxDurationMs
            });
        }
    }

    const exported = {
        PocketCounselorApiClient,
        NetworkError,
        RateLimitError,
        ServerError,
        ParseError,
        SchemaError,
        FlowError
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }
    globalScope.PocketCounselorApiClient = PocketCounselorApiClient;
    globalScope.PocketCounselorApiErrors = exported;
})(typeof globalThis !== 'undefined' ? globalThis : window);

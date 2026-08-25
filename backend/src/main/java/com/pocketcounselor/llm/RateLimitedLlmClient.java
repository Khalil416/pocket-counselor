package com.pocketcounselor.llm;

import com.pocketcounselor.config.AiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that paces and retries calls to the wrapped client.
 *
 * <p>This is where the Gemini free-tier artifacts live: a fixed pause after every
 * call, and a longer pause before retrying a rate-limited one. Both default to 0,
 * so providers that do not need pacing pay nothing.
 */
public class RateLimitedLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedLlmClient.class);

    private final LlmClient delegate;
    private final long delayMs;
    private final long retryDelayMs;
    private final int maxRetries;

    public RateLimitedLlmClient(LlmClient delegate, AiConfig.RateLimit settings) {
        this.delegate = delegate;
        this.delayMs = Math.max(0, settings.getDelayMs());
        this.retryDelayMs = Math.max(0, settings.getRetryDelayMs());
        this.maxRetries = Math.max(0, settings.getMaxRetries());
    }

    @Override
    public String complete(String prompt, double temperature) {
        LlmException lastError = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String text = delegate.complete(prompt, temperature);
                throttle();
                return text;
            } catch (LlmException e) {
                lastError = e;
                if (!e.isRetryable() || attempt == maxRetries) {
                    throw e;
                }
                if (e.isRateLimited()) {
                    log.warn("[AI] rate limited by {} (attempt {}/{}), waiting {}ms before retry",
                            e.getProvider(), attempt + 1, maxRetries + 1, retryDelayMs);
                } else {
                    log.warn("[AI] {} returned {} (attempt {}/{}), waiting {}ms before retry",
                            e.getProvider(), e.getStatusCode(), attempt + 1, maxRetries + 1, retryDelayMs);
                }
                if (!sleep(retryDelayMs)) throw e;
            }
        }

        throw lastError; // unreachable: the loop either returns or throws
    }

    /** Pause after a successful call to stay under the provider's request rate. */
    private void throttle() {
        sleep(delayMs);
    }

    /** @return false if the thread was interrupted while sleeping. */
    private boolean sleep(long millis) {
        if (millis <= 0) return true;
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

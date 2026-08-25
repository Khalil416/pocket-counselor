package com.pocketcounselor.llm;

/**
 * Shared failure type for every provider. Vendor error envelopes and transport
 * failures are both mapped onto this so callers -- and the rate-limiting
 * decorator -- never need to know which provider produced the failure.
 */
public class LlmException extends RuntimeException {

    /** Status code that caused the failure, or 0 when not HTTP-derived. */
    private final int statusCode;

    /** Provider key ("gemini", "openai", "anthropic"). */
    private final String provider;

    public LlmException(String provider, int statusCode, String message) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public LlmException(String provider, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }

    public String getProvider() { return provider; }

    /** True when the provider signalled rate limiting (HTTP 429). */
    public boolean isRateLimited() { return statusCode == 429; }

    /** True when a retry could plausibly succeed: rate limits and 5xx. */
    public boolean isRetryable() { return statusCode == 429 || (statusCode >= 500 && statusCode < 600); }
}

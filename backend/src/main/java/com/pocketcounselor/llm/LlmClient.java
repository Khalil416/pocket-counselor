package com.pocketcounselor.llm;

/**
 * A single text completion against some LLM provider.
 *
 * <p>Implementations own exactly one vendor's wire format: URL shape, auth
 * mechanism, request body, and response envelope. They know nothing about what
 * the prompt says or what shape the answer is expected to take.
 */
public interface LlmClient {

    /**
     * Send {@code prompt} to the model and return its raw text output.
     *
     * @param prompt      the fully-rendered prompt text
     * @param temperature sampling temperature, passed through to the provider
     * @return the model's raw text output (may contain markdown fences)
     * @throws LlmException if the provider returns an error or an unusable response
     */
    String complete(String prompt, double temperature);
}

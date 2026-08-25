package com.pocketcounselor.llm;

import com.pocketcounselor.config.AiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeSet;

/**
 * Resolves the active {@link LlmClient} from {@code ai.provider} and wraps it in
 * {@link RateLimitedLlmClient}.
 *
 * <p>Spring injects every provider client keyed by bean name ("gemini", "openai",
 * "anthropic"). Selection and validation happen once, at construction, so a bad
 * configuration fails the application start rather than the first request.
 *
 * <p>When {@code ai.mode=mock} no provider is required or validated -- mock mode
 * short-circuits inside {@code AiService} before any client is touched.
 */
@Component
public class LlmClientResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmClientResolver.class);

    private final LlmClient active;

    public LlmClientResolver(AiConfig aiConfig, Map<String, LlmClient> clientsByProvider) {
        if (aiConfig.isMockMode()) {
            log.info("[AI] ai.mode=mock -- no LLM provider will be contacted");
            this.active = null;
            return;
        }

        String provider = aiConfig.normalizedProvider();
        LlmClient selected = clientsByProvider.get(provider);

        if (selected == null) {
            throw new IllegalStateException(
                    "Unknown ai.provider '" + aiConfig.getProvider() + "'. Supported providers: "
                            + new TreeSet<>(clientsByProvider.keySet())
                            + ". Set ai.provider in application.properties, or use ai.mode=mock to run without a provider.");
        }

        requireConfigured(provider, aiConfig);

        this.active = new RateLimitedLlmClient(selected, aiConfig.getRatelimit());
        log.info("[AI] ai.mode=real, provider={}, model={}", provider, aiConfig.getActiveModel());
    }

    /** The rate-limited client for the selected provider. Never called in mock mode. */
    public LlmClient get() {
        if (active == null) {
            throw new IllegalStateException(
                    "No LLM client is available because ai.mode=mock. This is a bug: "
                            + "mock mode should short-circuit before reaching the client layer.");
        }
        return active;
    }

    private void requireConfigured(String provider, AiConfig aiConfig) {
        if (!aiConfig.isActiveKeyLoaded()) {
            throw new IllegalStateException(
                    "ai.mode=real and ai.provider=" + provider + ", but ai." + provider
                            + ".api-key is not set. Add it to application.properties, or set ai.mode=mock.");
        }
        if (aiConfig.getActiveModel() == null || aiConfig.getActiveModel().isBlank()) {
            throw new IllegalStateException(
                    "ai.mode=real and ai.provider=" + provider + ", but ai." + provider
                            + ".model is not set. Add it to application.properties.");
        }
    }
}

package com.pocketcounselor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All AI-related configuration, bound from the {@code ai.*} namespace.
 *
 * <p>This class is provider-neutral: it holds the mode, the selected provider,
 * shared rate-limit settings, and one settings block per supported vendor. It
 * contains no HTTP code -- see {@link LlmConfig} for bean wiring.
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    public static final String GEMINI = "gemini";
    public static final String OPENAI = "openai";
    public static final String ANTHROPIC = "anthropic";

    /** "mock" (no network, canned data) or "real". */
    private String mode = "mock";

    /** Which provider to use when mode=real: gemini | openai | anthropic. */
    private String provider = GEMINI;

    private Gemini gemini = new Gemini();
    private OpenAi openai = new OpenAi();
    private Anthropic anthropic = new Anthropic();
    private RateLimit ratelimit = new RateLimit();

    public boolean isMockMode() {
        return "mock".equalsIgnoreCase(mode);
    }

    /** Model name of the currently selected provider, for health reporting. */
    public String getActiveModel() {
        return switch (normalizedProvider()) {
            case OPENAI -> openai.getModel();
            case ANTHROPIC -> anthropic.getModel();
            default -> gemini.getModel();
        };
    }

    /** Whether the currently selected provider has a usable API key. */
    public boolean isActiveKeyLoaded() {
        return switch (normalizedProvider()) {
            case OPENAI -> openai.isApiKeyLoaded();
            case ANTHROPIC -> anthropic.isApiKeyLoaded();
            default -> gemini.isApiKeyLoaded();
        };
    }

    public String normalizedProvider() {
        return provider == null ? "" : provider.trim().toLowerCase();
    }

    // ----- getters / setters -----

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini gemini) { this.gemini = gemini; }

    public OpenAi getOpenai() { return openai; }
    public void setOpenai(OpenAi openai) { this.openai = openai; }

    public Anthropic getAnthropic() { return anthropic; }
    public void setAnthropic(Anthropic anthropic) { this.anthropic = anthropic; }

    public RateLimit getRatelimit() { return ratelimit; }
    public void setRatelimit(RateLimit ratelimit) { this.ratelimit = ratelimit; }

    // ----- nested settings blocks -----

    /** Settings common to every provider. */
    public abstract static class ProviderSettings {
        private String apiKey = "";
        private String model;
        private String baseUrl;
        private int timeoutSeconds = 30;

        /** A key is "loaded" only if non-blank and not the placeholder from the example file. */
        public boolean isApiKeyLoaded() {
            return apiKey != null && !apiKey.isBlank() && !"YOUR_KEY_HERE".equals(apiKey);
        }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Gemini extends ProviderSettings {
        public Gemini() {
            setModel("gemini-2.0-flash");
            setBaseUrl("https://generativelanguage.googleapis.com");
        }
    }

    public static class OpenAi extends ProviderSettings {
        /** Send response_format=json_object. Both prompt templates contain the word "json", which OpenAI requires. */
        private boolean jsonMode = true;

        /**
         * Send the temperature parameter. True by default because the default model
         * accepts it; GPT-5-class reasoning models reject any non-default value
         * with unsupported_value, so set this false for those.
         */
        private boolean sendTemperature = true;

        public OpenAi() {
            setModel("gpt-4o-mini");
            setBaseUrl("https://api.openai.com");
        }

        public boolean isJsonMode() { return jsonMode; }
        public void setJsonMode(boolean jsonMode) { this.jsonMode = jsonMode; }

        public boolean isSendTemperature() { return sendTemperature; }
        public void setSendTemperature(boolean sendTemperature) { this.sendTemperature = sendTemperature; }
    }

    public static class Anthropic extends ProviderSettings {
        /** Anthropic requires max_tokens on every request. */
        private int maxTokens = 16000;

        /** Value for the required anthropic-version header. */
        private String version = "2023-06-01";

        /**
         * Send the temperature parameter. False by default: current models
         * (Opus 5, Sonnet 5, Opus 4.7/4.8, Fable 5) reject sampling parameters
         * with a 400. Set true only for models that still accept them
         * (Opus 4.6, Sonnet 4.6, and older).
         */
        private boolean sendTemperature = false;

        public Anthropic() {
            setModel("claude-opus-5");
            setBaseUrl("https://api.anthropic.com");
        }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public boolean isSendTemperature() { return sendTemperature; }
        public void setSendTemperature(boolean sendTemperature) { this.sendTemperature = sendTemperature; }
    }

    /**
     * Rate-limit shaping applied by the decorator around the selected client.
     * Defaults to 0 -- only the Gemini free tier actually needs pacing.
     */
    public static class RateLimit {
        private long delayMs = 0;
        private long retryDelayMs = 0;
        private int maxRetries = 0;

        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }

        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }
}

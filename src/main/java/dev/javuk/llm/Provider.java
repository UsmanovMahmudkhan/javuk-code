package dev.javuk.llm;

/**
 * Presets for the OpenAI-compatible providers Javuk supports. Each maps to a
 * base URL and the environment variable that holds its API key. Selected via
 * {@code --provider} or the {@code JAVUK_PROVIDER} env var.
 */
public enum Provider {
    OPENROUTER("https://openrouter.ai/api/v1", "OPENROUTER_API_KEY"),
    OPENAI("https://api.openai.com/v1", "OPENAI_API_KEY"),
    OLLAMA("http://localhost:11434/v1", "OLLAMA_API_KEY");

    private final String baseUrl;
    private final String apiKeyEnv;

    Provider(String baseUrl, String apiKeyEnv) {
        this.baseUrl = baseUrl;
        this.apiKeyEnv = apiKeyEnv;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKeyEnv() {
        return apiKeyEnv;
    }

    /** Local providers like Ollama don't require a real key. */
    public String defaultApiKey() {
        return this == OLLAMA ? "ollama" : null;
    }

    public static Provider from(String s) {
        if (s == null || s.isBlank()) {
            return OPENROUTER;
        }
        return switch (s.toLowerCase()) {
            case "openai" -> OPENAI;
            case "ollama" -> OLLAMA;
            default -> OPENROUTER;
        };
    }
}

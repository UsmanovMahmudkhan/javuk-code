package dev.javuk.llm;

import dev.javuk.config.Config;

/** Picks the right {@link LlmClient} for the configured provider. */
public final class LlmClients {

    private LlmClients() {
    }

    public static LlmClient create(Config config, Usage usage) {
        if ("anthropic".equalsIgnoreCase(config.provider())) {
            return new AnthropicClient(config.apiKey(), config.model(), usage, config.maxTokens());
        }
        return new OpenAiCompatClient(config.apiKey(), config.baseUrl(), config.model(), usage,
                config.maxTokens());
    }
}

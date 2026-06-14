package dev.javuk.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {

    @Test
    void maxTokensDefaultsAndUpdates() {
        Config config = new Config();
        assertEquals(Config.DEFAULT_MAX_TOKENS, config.maxTokens());
        config.maxTokens(1234);
        assertEquals(1234, config.maxTokens());
    }

    @Test
    void maxTokensIgnoresNonPositive() {
        Config config = new Config().maxTokens(2048);
        config.maxTokens(0).maxTokens(-5);
        assertEquals(2048, config.maxTokens());
    }
}

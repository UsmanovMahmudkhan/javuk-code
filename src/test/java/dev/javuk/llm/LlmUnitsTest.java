package dev.javuk.llm;

import dev.javuk.permission.PermissionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmUnitsTest {

    @Test
    void providerPresets() {
        assertEquals(Provider.OPENAI, Provider.from("openai"));
        assertEquals(Provider.OLLAMA, Provider.from("ollama"));
        assertEquals(Provider.OPENROUTER, Provider.from("anything-else"));
        assertEquals("ollama", Provider.OLLAMA.defaultApiKey());
        assertTrue(Provider.OPENAI.baseUrl().startsWith("https://"));
    }

    @Test
    void permissionModeParsing() {
        assertEquals(PermissionMode.AUTO, PermissionMode.from("yolo"));
        assertEquals(PermissionMode.PLAN, PermissionMode.from("readonly"));
        assertEquals(PermissionMode.ASK, PermissionMode.from(null));
    }

    @Test
    void costEstimation() {
        // 1M input + 1M output of haiku at $1 / $5.
        double cost = ModelCatalog.estimateCost("anthropic/claude-haiku-4.5", 1_000_000, 1_000_000);
        assertEquals(6.0, cost, 0.0001);
        assertEquals(-1, ModelCatalog.estimateCost("unknown/model", 100, 100));
    }

    @Test
    void usageAccumulates() {
        Usage u = new Usage();
        u.add(100, 50);
        u.add(10, 5);
        assertEquals(110, u.promptTokens());
        assertEquals(55, u.completionTokens());
        assertEquals(165, u.totalTokens());
        assertEquals(2, u.requests());
    }
}

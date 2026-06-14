package dev.javuk.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRegistryTest {

    @Test
    void loadsBuiltInAgents(@TempDir Path dir) {
        AgentRegistry registry = new AgentRegistry(dir);
        assertTrue(registry.has("code-reviewer"));
        assertTrue(registry.has("planner"));
        assertTrue(registry.has("test-writer"));
        assertTrue(registry.has("explorer"));
        assertTrue(registry.get("explorer").restrictsTools());
    }

    @Test
    void projectFileOverridesBuiltInByName(@TempDir Path dir) throws Exception {
        Path agents = Files.createDirectories(dir.resolve(".javuk").resolve("agents"));
        Files.writeString(agents.resolve("explorer.md"),
                "---\ndescription: my custom explorer\ntools: Read\n---\nCustom body.");

        AgentRegistry registry = new AgentRegistry(dir);
        AgentDefinition def = registry.get("explorer");
        assertEquals("my custom explorer", def.description());
        assertEquals(List.of("Read"), def.tools());
        assertEquals("Custom body.", def.systemPrompt());
    }

    @Test
    void parsesFrontmatterFields() {
        AgentDefinition def = AgentDefinition.parse("fallback",
                "---\nname: reviewer\ndescription: reviews things\n"
                        + "tools: Read, Grep , Glob\nmodel: anthropic/claude-opus-4.1\n---\nBe thorough.");
        assertEquals("reviewer", def.name());
        assertEquals("reviews things", def.description());
        assertEquals(List.of("Read", "Grep", "Glob"), def.tools());
        assertEquals("anthropic/claude-opus-4.1", def.model());
        assertEquals("Be thorough.", def.systemPrompt());
        assertTrue(def.restrictsTools());
    }

    @Test
    void missingFrontmatterMeansWholeBodyIsPrompt() {
        AgentDefinition def = AgentDefinition.parse("helper", "Just do the thing.");
        assertEquals("helper", def.name());
        assertEquals("Just do the thing.", def.systemPrompt());
        assertNull(def.model());
        assertFalse(def.restrictsTools());
    }

    @Test
    void malformedAgentFileIsIgnored(@TempDir Path dir) throws Exception {
        Path agents = Files.createDirectories(dir.resolve(".javuk").resolve("agents"));
        Files.writeString(agents.resolve("weird.md"), "---\nnot really: frontmatter\n");

        // Loading must not throw; the file simply parses leniently.
        AgentRegistry registry = new AgentRegistry(dir);
        assertTrue(registry.has("code-reviewer")); // built-ins still present
    }
}

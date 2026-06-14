package dev.javuk.agent;

import dev.javuk.config.Config;
import dev.javuk.llm.AssistantTurn;
import dev.javuk.llm.ChatMessage;
import dev.javuk.llm.LlmClient;
import dev.javuk.llm.Usage;
import dev.javuk.permission.PermissionService;
import dev.javuk.tools.Tool;
import dev.javuk.tools.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubAgentAgentTypeTest {

    /** Captures the system prompt and tool set the agent loop passes to the model. */
    private static final class RecordingClient implements LlmClient {
        String systemPrompt;
        List<String> toolNames;

        @Override
        public AssistantTurn chat(String systemPrompt, List<ChatMessage> messages,
                                  List<Tool> tools, Consumer<String> onContentDelta) {
            this.systemPrompt = systemPrompt;
            this.toolNames = tools.stream().map(Tool::name).collect(Collectors.toList());
            return new AssistantTurn("done", List.of()); // no tool calls → final answer
        }

        @Override
        public String model() {
            return "stub";
        }
    }

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void selectedAgentRestrictsToolsAndUsesItsPrompt(@TempDir Path dir) {
        RecordingClient client = new RecordingClient();
        AgentRegistry agents = new AgentRegistry(dir);

        SubAgent.run(client, new Config(), new Usage(), ctx(dir), agents, "explorer", "find X");

        assertEquals(List.of("Read", "Grep", "Glob", "List"), client.toolNames);
        assertFalse(client.toolNames.contains("Write"));
        assertTrue(client.systemPrompt.toLowerCase().contains("explorer"));
    }

    @Test
    void unknownTypeFallsBackToGenericPersonaAndFullTools(@TempDir Path dir) {
        RecordingClient client = new RecordingClient();
        AgentRegistry agents = new AgentRegistry(dir);

        SubAgent.run(client, new Config(), new Usage(), ctx(dir), agents, "does-not-exist", "do it");

        assertTrue(client.systemPrompt.toLowerCase().contains("sub-agent"));
        assertTrue(client.toolNames.contains("Write"));
        assertTrue(client.toolNames.contains("Bash"));
        assertFalse(client.toolNames.contains("Task")); // sub-agents never get Task
    }

    @Test
    void nullTypeUsesGenericPersona(@TempDir Path dir) {
        RecordingClient client = new RecordingClient();
        SubAgent.run(client, new Config(), new Usage(), ctx(dir),
                new AgentRegistry(dir), null, "do it");
        assertTrue(client.systemPrompt.toLowerCase().contains("sub-agent"));
        assertTrue(client.toolNames.size() >= 10);
    }
}

package dev.javuk.llm;

import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolUnion;
import dev.javuk.tools.BashTool;
import dev.javuk.tools.ReadTool;
import dev.javuk.tools.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the Anthropic message/tool mapping and model normalization.
 * These are pure (no network / no API key) — they verify the translation layer
 * builds valid request objects and groups tool results correctly.
 */
class AnthropicClientTest {

    @Test
    void normalizesProviderPrefixedModelIds() {
        assertEquals("claude-sonnet-4-6", AnthropicClient.normalizeModel("anthropic/claude-sonnet-4.6"));
        assertEquals("claude-opus-4-8", AnthropicClient.normalizeModel("claude-opus-4-8"));
        assertEquals("claude-haiku-4-5", AnthropicClient.normalizeModel(null));
    }

    @Test
    void groupsToolResultsIntoFollowingUserTurn() {
        List<ChatMessage> messages = List.of(
                ChatMessage.user("hi"),
                ChatMessage.assistant("", List.of(new AssistantTurn.ToolCall("c1", "Read", "{}"))),
                ChatMessage.tool("c1", "file contents"),
                ChatMessage.assistant("done", List.of()));

        List<MessageParam> mapped = AnthropicClient.toAnthropicMessages(messages);

        // user, assistant(tool_use), user(tool_result), assistant(text)
        assertEquals(4, mapped.size());
        assertEquals(MessageParam.Role.USER, mapped.get(0).role());
        assertEquals(MessageParam.Role.ASSISTANT, mapped.get(1).role());
        assertEquals(MessageParam.Role.USER, mapped.get(2).role());
        assertEquals(MessageParam.Role.ASSISTANT, mapped.get(3).role());
    }

    @Test
    void buildsToolSpecsForEveryTool() {
        List<Tool> tools = List.of(new ReadTool(), new BashTool());
        List<ToolUnion> specs = AnthropicClient.toAnthropicTools(tools);
        assertEquals(2, specs.size());
        assertFalse(specs.isEmpty());
    }
}

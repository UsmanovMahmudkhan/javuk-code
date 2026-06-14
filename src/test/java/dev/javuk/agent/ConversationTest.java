package dev.javuk.agent;

import dev.javuk.llm.AssistantTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationTest {

    @Test
    void replayFillsDanglingToolCalls() {
        // Transcript cut off right after an assistant tool-call turn: no tool results recorded.
        List<Conversation.Entry> entries = List.of(
                Conversation.Entry.user("do it"),
                Conversation.Entry.assistant("calling tools", List.of(
                        new AssistantTurn.ToolCall("a", "Read", "{}"),
                        new AssistantTurn.ToolCall("b", "Bash", "{}"))));

        Conversation c = Conversation.fromEntries(entries, null);

        // Both tool calls must now be answered so the message sequence is valid.
        List<String> answered = c.entries().stream()
                .filter(e -> e.role().equals("tool"))
                .map(Conversation.Entry::toolCallId)
                .toList();
        assertEquals(List.of("a", "b"), answered);
        assertTrue(c.entries().stream()
                .filter(e -> e.role().equals("tool"))
                .allMatch(e -> e.content().contains("no result recorded")));
    }

    @Test
    void replayPreservesCompleteTranscript() {
        List<Conversation.Entry> entries = List.of(
                Conversation.Entry.user("hi"),
                Conversation.Entry.assistant("ok", List.of(
                        new AssistantTurn.ToolCall("x", "Read", "{}"))),
                Conversation.Entry.tool("x", "file contents"),
                Conversation.Entry.assistant("done", List.of()));

        Conversation c = Conversation.fromEntries(entries, null);

        long toolResults = c.entries().stream().filter(e -> e.role().equals("tool")).count();
        assertEquals(1, toolResults); // no spurious placeholders added
    }
}

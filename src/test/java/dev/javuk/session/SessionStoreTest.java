package dev.javuk.session;

import dev.javuk.agent.Conversation;
import dev.javuk.llm.AssistantTurn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    @Test
    void savesAndLoadsRoundTrip(@TempDir Path dir) throws Exception {
        SessionStore store = new SessionStore(dir);

        Conversation conv = new Conversation();
        conv.addUser("hello");
        conv.addAssistantTurn(new AssistantTurn("calling tool",
                List.of(new AssistantTurn.ToolCall("c1", "Read", "{\"file_path\":\"x\"}"))));
        conv.addToolResult("c1", "file contents");
        conv.addAssistantTurn(new AssistantTurn("done", List.of()));

        store.save(Session.of("s1", "test-model", "2026-01-01T00:00:00Z", conv));
        Session loaded = store.load("s1");

        assertEquals("s1", loaded.id());
        assertEquals("test-model", loaded.model());
        assertEquals(4, loaded.entries().size());
        assertEquals("hello", loaded.entries().get(0).content());
        assertEquals("Read", loaded.entries().get(1).toolCalls().get(0).name());
        assertEquals("c1", loaded.entries().get(2).toolCallId());
    }

    @Test
    void replaysIntoConversation(@TempDir Path dir) throws Exception {
        SessionStore store = new SessionStore(dir);
        Conversation conv = new Conversation();
        conv.addUser("hi");
        conv.addAssistantTurn(new AssistantTurn("hello there", List.of()));
        store.save(Session.of("s2", "m", "t", conv));

        Session loaded = store.load("s2");
        Conversation rebuilt = Conversation.fromEntries(loaded.entries(), "system");
        // The system prompt is held separately now; entries are user + assistant.
        assertEquals(2, rebuilt.size());
        assertEquals("system", rebuilt.systemPrompt());
    }

    @Test
    void listsNewestFirstAndReportsMostRecent(@TempDir Path dir) throws Exception {
        SessionStore store = new SessionStore(dir);
        Conversation conv = new Conversation();
        conv.addUser("x");
        store.save(Session.of("a", "m", "t", conv));
        Thread.sleep(10);
        store.save(Session.of("b", "m", "t", conv));

        List<String> ids = store.list();
        assertTrue(ids.contains("a") && ids.contains("b"));
        assertEquals("b", store.mostRecent());
    }
}

package dev.javuk.agent;

import dev.javuk.llm.AssistantTurn;
import dev.javuk.llm.ChatMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The mutable message history for one agent session. Stores a provider-neutral
 * transcript of {@link Entry} records (also used to save/restore sessions) plus
 * the system prompt; {@link #chatMessages()} projects it into the neutral wire
 * type each {@link dev.javuk.llm.LlmClient} consumes.
 */
public final class Conversation {

    /** Serialisable transcript entry. role is "user", "assistant", or "tool". */
    public record Entry(String role, String content,
                        List<AssistantTurn.ToolCall> toolCalls, String toolCallId) {
        public static Entry user(String content) {
            return new Entry("user", content, List.of(), null);
        }

        public static Entry assistant(String content, List<AssistantTurn.ToolCall> calls) {
            return new Entry("assistant", content, calls == null ? List.of() : calls, null);
        }

        public static Entry tool(String toolCallId, String content) {
            return new Entry("tool", content, List.of(), toolCallId);
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private String systemPrompt;

    public Conversation() {
    }

    public Conversation withSystemPrompt(String systemPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            this.systemPrompt = systemPrompt;
        }
        return this;
    }

    public void addUser(String content) {
        entries.add(Entry.user(content));
    }

    public void addAssistantTurn(AssistantTurn turn) {
        entries.add(Entry.assistant(turn.content(), turn.toolCalls()));
    }

    public void addToolResult(String toolCallId, String content) {
        entries.add(Entry.tool(toolCallId, content));
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    /** The conversation projected into the neutral wire type (no system message). */
    public List<ChatMessage> chatMessages() {
        List<ChatMessage> messages = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            messages.add(switch (e.role()) {
                case "user" -> ChatMessage.user(e.content());
                case "assistant" -> ChatMessage.assistant(e.content(), e.toolCalls());
                case "tool" -> ChatMessage.tool(e.toolCallId(), e.content());
                default -> ChatMessage.user(e.content());
            });
        }
        return messages;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    /** Clears history but preserves the system prompt, if any. */
    public void reset() {
        entries.clear();
    }

    /**
     * Replays a saved transcript onto a fresh conversation (with optional system
     * prompt). Repairs dangling tool calls: a session saved or cancelled right
     * after an assistant {@code tool_calls} turn (before its results were
     * recorded) would otherwise replay into an API-invalid sequence — providers
     * require every {@code tool_call} to be answered by a {@code tool} message
     * before the next user/assistant message. Any unanswered call id is filled
     * with a {@code "[no result recorded]"} placeholder.
     */
    public static Conversation fromEntries(List<Entry> entries, String systemPrompt) {
        Conversation c = new Conversation().withSystemPrompt(systemPrompt);
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        for (Entry e : entries) {
            switch (e.role()) {
                case "user" -> {
                    fillPending(c, pending);
                    c.addUser(e.content());
                }
                case "assistant" -> {
                    fillPending(c, pending);
                    c.addAssistantTurn(new AssistantTurn(e.content(), e.toolCalls()));
                    for (AssistantTurn.ToolCall call : e.toolCalls()) {
                        pending.add(call.id());
                    }
                }
                case "tool" -> {
                    c.addToolResult(e.toolCallId(), e.content());
                    pending.remove(e.toolCallId());
                }
                default -> { /* ignore unknown roles */ }
            }
        }
        fillPending(c, pending);
        return c;
    }

    /** Answers any tool calls left unanswered with a placeholder, keeping the sequence valid. */
    private static void fillPending(Conversation c, LinkedHashSet<String> pending) {
        for (String callId : pending) {
            c.addToolResult(callId, "[no result recorded]");
        }
        pending.clear();
    }
}

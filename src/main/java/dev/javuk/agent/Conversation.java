package dev.javuk.agent;

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import dev.javuk.llm.AssistantTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutable message history for one agent session. Maintains two parallel
 * views: the SDK request params sent to the model, and a serialisable
 * {@link Entry} transcript used to save/restore sessions.
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

    private final List<ChatCompletionMessageParam> messages = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private ChatCompletionMessageParam systemMessage;

    public Conversation() {
    }

    public Conversation withSystemPrompt(String systemPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            systemMessage = ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(systemPrompt).build());
            messages.add(systemMessage);
        }
        return this;
    }

    public void addUser(String content) {
        messages.add(ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(content).build()));
        entries.add(Entry.user(content));
    }

    /** Records one assistant turn: builds the SDK param and the transcript entry. */
    public void addAssistantTurn(AssistantTurn turn) {
        messages.add(ChatCompletionMessageParam.ofAssistant(toParam(turn)));
        entries.add(Entry.assistant(turn.content(), turn.toolCalls()));
    }

    public void addToolResult(String toolCallId, String content) {
        messages.add(ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam.builder()
                        .toolCallId(toolCallId)
                        .content(content)
                        .build()));
        entries.add(Entry.tool(toolCallId, content));
    }

    public List<ChatCompletionMessageParam> messages() {
        return messages;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return messages.size();
    }

    /** Clears history but preserves the system message, if any. */
    public void reset() {
        messages.clear();
        entries.clear();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
    }

    /** Replays a saved transcript onto a fresh conversation (with optional system prompt). */
    public static Conversation fromEntries(List<Entry> entries, String systemPrompt) {
        Conversation c = new Conversation().withSystemPrompt(systemPrompt);
        for (Entry e : entries) {
            switch (e.role()) {
                case "user" -> c.addUser(e.content());
                case "assistant" -> c.addAssistantTurn(
                        new AssistantTurn(e.content(), e.toolCalls()));
                case "tool" -> c.addToolResult(e.toolCallId(), e.content());
                default -> { /* ignore unknown roles */ }
            }
        }
        return c;
    }

    /** Rebuilds the request-side assistant message (content + tool calls) for history. */
    private static ChatCompletionAssistantMessageParam toParam(AssistantTurn turn) {
        ChatCompletionAssistantMessageParam.Builder builder =
                ChatCompletionAssistantMessageParam.builder();
        if (turn.content() != null && !turn.content().isEmpty()) {
            builder.content(turn.content());
        }
        if (turn.hasToolCalls()) {
            List<ChatCompletionMessageToolCall> calls = new ArrayList<>();
            for (AssistantTurn.ToolCall c : turn.toolCalls()) {
                calls.add(ChatCompletionMessageToolCall.builder()
                        .id(c.id())
                        .function(ChatCompletionMessageToolCall.Function.builder()
                                .name(c.name())
                                .arguments(c.arguments())
                                .build())
                        .build());
            }
            builder.toolCalls(calls);
        }
        return builder.build();
    }
}

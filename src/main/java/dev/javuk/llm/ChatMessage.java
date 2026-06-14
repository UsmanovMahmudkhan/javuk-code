package dev.javuk.llm;

import java.util.List;

/**
 * Provider-neutral chat message handed to an {@link LlmClient}. Each client
 * translates these into its own SDK request types, so the agent and conversation
 * never depend on a specific provider's message classes.
 *
 * <p>{@code role} is {@code "user"}, {@code "assistant"}, or {@code "tool"}.
 * Assistant messages may carry {@code toolCalls}; tool messages carry the
 * {@code toolCallId} they answer.
 */
public record ChatMessage(String role, String content,
                          List<AssistantTurn.ToolCall> toolCalls, String toolCallId) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, List.of(), null);
    }

    public static ChatMessage assistant(String content, List<AssistantTurn.ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls == null ? List.of() : toolCalls, null);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, List.of(), toolCallId);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

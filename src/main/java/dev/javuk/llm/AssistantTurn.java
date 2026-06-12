package dev.javuk.llm;

import java.util.List;

/**
 * Provider-neutral result of one model turn: the assistant's text plus any tool
 * calls it requested. Decouples the agent loop from the SDK's response types so
 * streaming and non-streaming clients return the same shape.
 */
public record AssistantTurn(String content, List<ToolCall> toolCalls) {

    /** A single tool invocation the model asked for. */
    public record ToolCall(String id, String name, String arguments) {
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

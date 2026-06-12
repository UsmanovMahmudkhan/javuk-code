package dev.javuk.agent;

/**
 * UI hook into the agent loop. The REPL implements this to render spinners,
 * tool activity, and assistant text live; one-shot mode uses {@link #noop()}.
 */
public interface AgentListener {

    /** Called just before each request to the model (UI can show a spinner). */
    default void onThinking() {
    }

    /** Called with each streamed text fragment as the model produces it. */
    default void onAssistantDelta(String fragment) {
    }

    /** Called when the model decides to invoke a tool, before it runs. */
    default void onToolCall(String name, String arguments) {
    }

    /** Called with the tool's result after it runs. */
    default void onToolResult(String name, String result) {
    }

    /** Called with the model's final natural-language answer for a turn. */
    default void onAssistantText(String text) {
    }

    static AgentListener noop() {
        return new AgentListener() {
        };
    }
}

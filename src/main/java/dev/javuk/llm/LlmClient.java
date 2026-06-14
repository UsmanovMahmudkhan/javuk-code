package dev.javuk.llm;

import dev.javuk.tools.Tool;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-neutral chat interface. Inputs are Javuk's own neutral types
 * ({@link ChatMessage}, {@link Tool}) so the agent loop never depends on any
 * provider SDK. Implementations translate them to their wire format:
 * {@link OpenAiCompatClient} (OpenRouter / OpenAI / Ollama) and
 * {@link AnthropicClient} (native Anthropic API).
 */
public interface LlmClient {

    /**
     * Sends the system prompt + conversation + tool specs and returns the
     * assistant turn (streaming text fragments to {@code onContentDelta}).
     *
     * @param systemPrompt   the system prompt, or null/blank for none
     * @param messages       the conversation so far (no system message)
     * @param tools          the tools the model may call
     * @param onContentDelta invoked with each streamed text fragment as it
     *                       arrives; pass a no-op consumer for non-interactive use
     */
    AssistantTurn chat(String systemPrompt,
                       List<ChatMessage> messages,
                       List<Tool> tools,
                       Consumer<String> onContentDelta);

    /** The model id this client talks to (for display and cost lookups). */
    String model();
}

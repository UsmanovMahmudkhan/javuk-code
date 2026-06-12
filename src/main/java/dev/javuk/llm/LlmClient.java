package dev.javuk.llm;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provider-agnostic chat interface. All current providers (OpenRouter, OpenAI,
 * Ollama, Anthropic-via-OpenRouter) speak the OpenAI-compatible protocol and are
 * served by {@link OpenAiCompatClient}; this interface is the seam where a truly
 * different provider could be slotted in.
 */
public interface LlmClient {

    /**
     * Sends the conversation + tool specs and returns the assistant turn.
     *
     * @param onContentDelta invoked with each streamed text fragment as it
     *                       arrives; pass a no-op consumer for non-interactive use.
     */
    AssistantTurn chat(List<ChatCompletionMessageParam> messages,
                       List<ChatCompletionTool> tools,
                       Consumer<String> onContentDelta);

    /** The model id this client talks to (for display and cost lookups). */
    String model();
}

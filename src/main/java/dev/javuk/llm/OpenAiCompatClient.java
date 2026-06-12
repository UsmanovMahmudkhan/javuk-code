package dev.javuk.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * {@link LlmClient} backed by the OpenAI Java SDK. Works against any
 * OpenAI-compatible endpoint by varying the base URL — OpenRouter (default),
 * OpenAI, a local Ollama server, etc.
 *
 * <p>Responses are streamed: text fragments are forwarded to {@code onContentDelta}
 * as they arrive while tool-call deltas are accumulated by index and assembled
 * into the returned {@link AssistantTurn}. Token usage is captured from the final
 * chunk into {@link Usage}.
 */
public final class OpenAiCompatClient implements LlmClient {

    private final OpenAIClient client;
    private final String model;
    private final Usage usage;

    public OpenAiCompatClient(String apiKey, String baseUrl, String model, Usage usage) {
        // The SDK retries retryable failures (429 / 5xx / timeouts) with backoff.
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(4)
                .timeout(Timeout.builder().request(Duration.ofMinutes(5)).build())
                .build();
        this.model = model;
        this.usage = usage;
    }

    @Override
    public AssistantTurn chat(List<ChatCompletionMessageParam> messages,
                              List<ChatCompletionTool> tools,
                              Consumer<String> onContentDelta) {
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
        for (ChatCompletionTool tool : tools) {
            params.addTool(tool);
        }

        StringBuilder content = new StringBuilder();
        // Keyed by tool-call index so out-of-order deltas still assemble correctly.
        TreeMap<Long, PartialToolCall> partials = new TreeMap<>();

        try (StreamResponse<ChatCompletionChunk> response =
                     client.chat().completions().createStreaming(params.build())) {
            Stream<ChatCompletionChunk> stream = response.stream();
            for (ChatCompletionChunk chunk : (Iterable<ChatCompletionChunk>) stream::iterator) {
                if (Thread.currentThread().isInterrupted()) {
                    break; // user cancelled; try-with-resources closes the stream
                }
                chunk.usage().ifPresent(u -> usage.add(u.promptTokens(), u.completionTokens()));
                if (chunk.choices().isEmpty()) {
                    continue;
                }
                var delta = chunk.choices().get(0).delta();
                delta.content().ifPresent(text -> {
                    if (!text.isEmpty()) {
                        content.append(text);
                        onContentDelta.accept(text);
                    }
                });
                delta.toolCalls().ifPresent(calls -> {
                    for (var tc : calls) {
                        PartialToolCall p = partials.computeIfAbsent(tc.index(), k -> new PartialToolCall());
                        tc.id().ifPresent(id -> p.id = id);
                        tc.function().ifPresent(fn -> {
                            fn.name().ifPresent(n -> p.name = n);
                            fn.arguments().ifPresent(a -> p.arguments.append(a));
                        });
                    }
                });
            }
        }

        List<AssistantTurn.ToolCall> toolCalls = new ArrayList<>();
        for (Map.Entry<Long, PartialToolCall> e : partials.entrySet()) {
            PartialToolCall p = e.getValue();
            if (p.name != null) {
                toolCalls.add(new AssistantTurn.ToolCall(
                        p.id != null ? p.id : "call_" + e.getKey(),
                        p.name,
                        p.arguments.toString()));
            }
        }
        return new AssistantTurn(content.toString(), toolCalls);
    }

    @Override
    public String model() {
        return model;
    }

    /** Mutable accumulator for a streamed tool call. */
    private static final class PartialToolCall {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}

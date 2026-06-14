package dev.javuk.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.Timeout;
import com.openai.core.http.StreamResponse;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import dev.javuk.tools.Tool;

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
 * <p>Neutral {@link ChatMessage}/{@link Tool} inputs are translated into SDK
 * request types here. Responses are streamed: text fragments are forwarded to
 * {@code onContentDelta} as they arrive while tool-call deltas are accumulated by
 * index and assembled into the returned {@link AssistantTurn}. Token usage is
 * captured from the final chunk into {@link Usage}.
 */
public final class OpenAiCompatClient implements LlmClient {

    private final OpenAIClient client;
    private final String model;
    private final Usage usage;
    private final long maxTokens;

    public OpenAiCompatClient(String apiKey, String baseUrl, String model, Usage usage) {
        this(apiKey, baseUrl, model, usage, dev.javuk.config.Config.DEFAULT_MAX_TOKENS);
    }

    public OpenAiCompatClient(String apiKey, String baseUrl, String model, Usage usage, int maxTokens) {
        // The SDK retries retryable failures (429 / 5xx / timeouts) with backoff.
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .maxRetries(4)
                .timeout(Timeout.builder().request(Duration.ofMinutes(5)).build())
                .build();
        this.model = model;
        this.usage = usage;
        this.maxTokens = maxTokens;
    }

    @Override
    public AssistantTurn chat(String systemPrompt,
                              List<ChatMessage> messages,
                              List<Tool> tools,
                              Consumer<String> onContentDelta) {
        ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens) // sent as max_tokens — universally honoured (incl. OpenRouter)
                .messages(toOpenAiMessages(systemPrompt, messages))
                .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build());
        for (ChatCompletionTool tool : toOpenAiTools(tools)) {
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

    /** Builds the SDK message list (system message first) from neutral messages. */
    private static List<ChatCompletionMessageParam> toOpenAiMessages(
            String systemPrompt, List<ChatMessage> messages) {
        List<ChatCompletionMessageParam> out = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            out.add(ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(systemPrompt).build()));
        }
        for (ChatMessage m : messages) {
            switch (m.role()) {
                case "user" -> out.add(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(m.content()).build()));
                case "assistant" -> out.add(ChatCompletionMessageParam.ofAssistant(toAssistantParam(m)));
                case "tool" -> out.add(ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(m.toolCallId())
                                .content(m.content())
                                .build()));
                default -> { /* ignore unknown roles */ }
            }
        }
        return out;
    }

    private static ChatCompletionAssistantMessageParam toAssistantParam(ChatMessage m) {
        ChatCompletionAssistantMessageParam.Builder builder =
                ChatCompletionAssistantMessageParam.builder();
        if (m.content() != null && !m.content().isEmpty()) {
            builder.content(m.content());
        }
        if (m.hasToolCalls()) {
            List<ChatCompletionMessageToolCall> calls = new ArrayList<>();
            for (AssistantTurn.ToolCall c : m.toolCalls()) {
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

    /** Converts neutral tools into OpenAI function-tool specs. */
    private static List<ChatCompletionTool> toOpenAiTools(List<Tool> tools) {
        List<ChatCompletionTool> specs = new ArrayList<>();
        for (Tool tool : tools) {
            FunctionParameters fnParams = FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(tool.properties()))
                    .putAdditionalProperty("required", JsonValue.from(tool.required()))
                    .build();
            specs.add(ChatCompletionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(fnParams)
                            .build())
                    .build());
        }
        return specs;
    }

    /** Mutable accumulator for a streamed tool call. */
    private static final class PartialToolCall {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }
}

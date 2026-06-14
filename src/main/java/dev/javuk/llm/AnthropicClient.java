package dev.javuk.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlockParam;
import dev.javuk.util.Json;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * {@link LlmClient} backed by the <b>native</b> Anthropic Java SDK — not the
 * OpenAI-compatibility shim. Talking to the Messages API directly unlocks
 * prompt caching (the system prompt + tool specs are marked {@code ephemeral} so
 * the cached prefix is reused across the agent loop's many turns), native tool
 * use, and accurate token accounting.
 *
 * <p>Neutral {@link ChatMessage}s are translated to Anthropic's block format:
 * assistant tool calls become {@code tool_use} blocks and tool results become
 * {@code tool_result} blocks grouped into the following user turn.
 */
public final class AnthropicClient implements LlmClient {

    // Fully-qualified to avoid a clash with this class's own name.
    private final com.anthropic.client.AnthropicClient client;
    private final String model;
    private final Usage usage;
    private final long maxTokens;

    public AnthropicClient(String apiKey, String model, Usage usage) {
        this(apiKey, model, usage, dev.javuk.config.Config.DEFAULT_MAX_TOKENS);
    }

    public AnthropicClient(String apiKey, String model, Usage usage, int maxTokens) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofMinutes(5))
                .maxRetries(4)
                .build();
        this.model = normalizeModel(model);
        this.usage = usage;
        this.maxTokens = maxTokens;
    }

    /** Native model ids have no {@code anthropic/} provider prefix. */
    static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "claude-haiku-4-5";
        }
        String m = model.startsWith("anthropic/") ? model.substring("anthropic/".length()) : model;
        return m.replace("claude-haiku-4.5", "claude-haiku-4-5")
                .replace("claude-sonnet-4.6", "claude-sonnet-4-6")
                .replace("claude-opus-4.8", "claude-opus-4-8");
    }

    @Override
    public AssistantTurn chat(String systemPrompt,
                              List<ChatMessage> messages,
                              List<dev.javuk.tools.Tool> tools,
                              Consumer<String> onContentDelta) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .messages(toAnthropicMessages(messages));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            // Cache the (large, stable) system prompt across the loop's turns.
            params.systemOfTextBlockParams(List.of(TextBlockParam.builder()
                    .text(systemPrompt)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()));
        }
        List<ToolUnion> toolSpecs = toAnthropicTools(tools);
        if (!toolSpecs.isEmpty()) {
            params.tools(toolSpecs);
        }

        StringBuilder content = new StringBuilder();
        MessageAccumulator accumulator = MessageAccumulator.create();

        try (StreamResponse<RawMessageStreamEvent> response =
                     client.messages().createStreaming(params.build())) {
            Stream<RawMessageStreamEvent> stream = response.stream();
            for (RawMessageStreamEvent event : (Iterable<RawMessageStreamEvent>) stream::iterator) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                accumulator.accumulate(event);
                event.contentBlockDelta().ifPresent(delta ->
                        delta.delta().text().ifPresent(text -> {
                            if (!text.text().isEmpty()) {
                                content.append(text.text());
                                onContentDelta.accept(text.text());
                            }
                        }));
            }
        }

        Message message = accumulator.message();
        com.anthropic.models.messages.Usage u = message.usage();
        usage.add(u.inputTokens(), u.outputTokens());

        List<AssistantTurn.ToolCall> toolCalls = new ArrayList<>();
        message.content().forEach(block -> block.toolUse().ifPresent(tu ->
                toolCalls.add(new AssistantTurn.ToolCall(tu.id(), tu.name(), inputToJson(tu._input())))));

        return new AssistantTurn(content.toString(), toolCalls);
    }

    @Override
    public String model() {
        return model;
    }

    /** Translates neutral messages into Anthropic message params, grouping tool results. */
    static List<MessageParam> toAnthropicMessages(List<ChatMessage> messages) {
        List<MessageParam> out = new ArrayList<>();
        List<ContentBlockParam> pendingToolResults = new ArrayList<>();

        for (ChatMessage m : messages) {
            switch (m.role()) {
                case "tool" -> pendingToolResults.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder()
                                .toolUseId(m.toolCallId())
                                .content(m.content() == null ? "" : m.content())
                                .build()));
                case "user" -> {
                    flushToolResults(out, pendingToolResults);
                    out.add(userText(m.content()));
                }
                case "assistant" -> {
                    flushToolResults(out, pendingToolResults);
                    MessageParam assistant = assistantBlocks(m);
                    if (assistant != null) {
                        out.add(assistant);
                    }
                }
                default -> { /* ignore */ }
            }
        }
        flushToolResults(out, pendingToolResults);
        return out;
    }

    private static void flushToolResults(List<MessageParam> out, List<ContentBlockParam> pending) {
        if (!pending.isEmpty()) {
            out.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(List.copyOf(pending))
                    .build());
            pending.clear();
        }
    }

    private static MessageParam userText(String content) {
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(content == null || content.isEmpty() ? " " : content)
                .build();
    }

    /** @return the assistant message, or null if it has neither text nor tool calls. */
    private static MessageParam assistantBlocks(ChatMessage m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (m.content() != null && !m.content().isEmpty()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content()).build()));
        }
        for (AssistantTurn.ToolCall call : m.toolCalls()) {
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(call.id())
                    .name(call.name())
                    .input(parseInput(call.arguments()))
                    .build()));
        }
        if (blocks.isEmpty()) {
            return null;
        }
        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(blocks)
                .build();
    }

    /** Converts tool specs to Anthropic tools; caches the tool prefix on the last one. */
    static List<ToolUnion> toAnthropicTools(List<dev.javuk.tools.Tool> tools) {
        List<ToolUnion> out = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            dev.javuk.tools.Tool t = tools.get(i);
            Tool.InputSchema schema = Tool.InputSchema.builder()
                    .properties(JsonValue.from(t.properties()))
                    .putAdditionalProperty("required", JsonValue.from(t.required()))
                    .build();
            Tool.Builder b = Tool.builder()
                    .name(t.name())
                    .description(t.description())
                    .inputSchema(schema);
            if (i == tools.size() - 1) {
                b.cacheControl(CacheControlEphemeral.builder().build());
            }
            out.add(ToolUnion.ofTool(b.build()));
        }
        return out;
    }

    /** Parses a JSON-string tool-argument blob into a JsonValue for the request. */
    private static JsonValue parseInput(String argumentsJson) {
        try {
            if (argumentsJson == null || argumentsJson.isBlank()) {
                return JsonValue.from(Map.of());
            }
            Object parsed = Json.MAPPER.readValue(argumentsJson, Object.class);
            return JsonValue.from(parsed);
        } catch (Exception e) {
            return JsonValue.from(Map.of());
        }
    }

    /** Serialises a tool_use input value back to a JSON string for the agent's dispatcher. */
    private static String inputToJson(JsonValue input) {
        try {
            return Json.MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }
}

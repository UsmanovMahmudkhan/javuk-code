package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletionTool;
import dev.javuk.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the set of tools available to the agent. Converts each {@link Tool}
 * into the OpenAI-compatible {@link ChatCompletionTool} spec and dispatches
 * tool calls by name, enforcing permissions for mutating tools.
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    /** Builds the tool specs to send with each chat-completion request. */
    public List<ChatCompletionTool> specs() {
        List<ChatCompletionTool> specs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            FunctionParameters params = FunctionParameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(tool.properties()))
                    .putAdditionalProperty("required", JsonValue.from(tool.required()))
                    .build();
            specs.add(ChatCompletionTool.builder()
                    .function(FunctionDefinition.builder()
                            .name(tool.name())
                            .description(tool.description())
                            .parameters(params)
                            .build())
                    .build());
        }
        return specs;
    }

    /**
     * Parses arguments, checks permission, and runs the named tool. Never throws:
     * failures (unknown tool, denied permission, execution error) are returned as
     * a string so the model can see and react to them.
     */
    public String dispatch(String name, String rawArguments, ToolContext ctx) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "Error: unknown tool '" + name + "'";
        }
        JsonNode args;
        try {
            args = Json.parse(rawArguments);
        } catch (Exception e) {
            return "Error: could not parse arguments for " + name + ": " + e.getMessage();
        }

        if (tool.mutating()) {
            String preview;
            try {
                preview = tool.preview(args, ctx);
            } catch (Exception e) {
                preview = name + " " + args;
            }
            if (!ctx.permissions().allow(name, true, preview)) {
                return "Permission denied by user: the " + name + " action was not allowed.";
            }
        }

        if (!ctx.hooks().isEmpty()) {
            String denial = ctx.hooks().runPre(name, rawArguments, ctx.workingDir());
            if (denial != null) {
                return "Blocked by pre-tool hook: " + denial;
            }
        }

        String result;
        try {
            result = tool.execute(args, ctx);
        } catch (Exception e) {
            dev.javuk.util.Logging.error("tool " + name + " failed", e);
            return "Error executing " + name + ": " + e.getMessage();
        }

        if (!ctx.hooks().isEmpty()) {
            ctx.hooks().runPost(name, rawArguments, ctx.workingDir());
        }
        return result;
    }
}

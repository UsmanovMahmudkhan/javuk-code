package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the set of tools available to the agent and dispatches tool calls by
 * name, enforcing permissions for mutating tools. Each LLM client converts
 * {@link #all()} into its own provider-specific tool spec.
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

    /**
     * A new registry containing only the named tools that exist here. Matching is
     * case-insensitive against {@link Tool#name()}; unknown names are ignored. Used
     * to enforce an agent persona's restricted tool set.
     */
    public ToolRegistry subset(Collection<String> names) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String n : tools.keySet()) {
            byLower.put(n.toLowerCase(), n);
        }
        ToolRegistry sub = new ToolRegistry();
        for (String requested : names) {
            if (requested == null) {
                continue;
            }
            String actual = byLower.get(requested.strip().toLowerCase());
            if (actual != null) {
                sub.register(tools.get(actual));
            }
        }
        return sub;
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

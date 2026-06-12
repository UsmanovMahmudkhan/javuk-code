package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A capability the agent can invoke through OpenAI-compatible function calling.
 * Each tool advertises a JSON-schema for its arguments and runs against a
 * {@link ToolContext}.
 */
public interface Tool {

    /** Function name exposed to the model (e.g. "Read", "Bash"). */
    String name();

    /** One-line description the model uses to decide when to call this tool. */
    String description();

    /**
     * JSON-schema {@code properties} map for the tool's arguments. Keys are
     * argument names, values are schema fragments (type/description/...).
     */
    Map<String, Object> properties();

    /** Argument names that must be present. */
    java.util.List<String> required();

    /**
     * Whether invoking this tool changes state (writes files, runs commands).
     * Read-only tools never trigger a permission prompt.
     */
    default boolean mutating() {
        return false;
    }

    /** Executes the tool and returns a result string fed back to the model. */
    String execute(JsonNode args, ToolContext ctx) throws Exception;

    /**
     * Human-readable preview of what executing this tool would do, shown in the
     * permission prompt. Mutating tools override this to render a diff or the
     * exact command. Never throws — returns a best-effort summary.
     */
    default String preview(JsonNode args, ToolContext ctx) {
        return name() + " " + args;
    }
}

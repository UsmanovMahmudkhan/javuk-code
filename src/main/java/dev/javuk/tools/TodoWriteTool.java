package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets the agent maintain a visible task list while working on multi-step jobs.
 * The latest list replaces the previous one and is rendered with status markers.
 * State lives in this (session-scoped) tool instance.
 */
public final class TodoWriteTool implements Tool {

    private final List<String> rendered = new ArrayList<>();

    @Override
    public String name() {
        return "TodoWrite";
    }

    @Override
    public String description() {
        return "Record or update the current task list. Pass the full list each time; statuses "
                + "are pending, in_progress, or completed.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "todos", Map.of(
                        "type", "array",
                        "description", "The full, current task list",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "content", Map.of("type", "string"),
                                        "status", Map.of("type", "string",
                                                "enum", List.of("pending", "in_progress", "completed"))),
                                "required", List.of("content", "status")))
        );
    }

    @Override
    public List<String> required() {
        return List.of("todos");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        JsonNode todos = args.get("todos");
        if (todos == null || !todos.isArray()) {
            return "Error: 'todos' must be an array";
        }
        rendered.clear();
        StringBuilder sb = new StringBuilder("Tasks:\n");
        for (JsonNode t : todos) {
            String content = Json.str(t, "content", "");
            String status = Json.str(t, "status", "pending");
            String marker = switch (status) {
                case "completed" -> "[x]";
                case "in_progress" -> "[~]";
                default -> "[ ]";
            };
            String line = marker + " " + content;
            rendered.add(line);
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }

    /** Current rendered list — used by the REPL to display tasks. */
    public List<String> snapshot() {
        return List.copyOf(rendered);
    }
}

package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.util.List;
import java.util.Map;

/**
 * Delegates a self-contained sub-task to a nested agent. The model uses this to
 * break a large job into focused pieces; each invocation runs its own agent loop
 * and returns a summary. The actual sub-agent execution is supplied by a
 * {@link Runner} wired in at startup (it captures the LLM client and context).
 */
public final class TaskTool implements Tool {

    /** Runs the delegated prompt and returns the sub-agent's final answer. */
    public interface Runner {
        String run(String description, String prompt);
    }

    private final Runner runner;

    public TaskTool(Runner runner) {
        this.runner = runner;
    }

    @Override
    public String name() {
        return "Task";
    }

    @Override
    public String description() {
        return "Delegate a self-contained task to a sub-agent that has its own tools and "
                + "context. Use for focused, multi-step subtasks; returns the sub-agent's summary.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "description", Map.of("type", "string", "description", "Short label for the subtask"),
                "prompt", Map.of("type", "string", "description", "The full instructions for the sub-agent")
        );
    }

    @Override
    public List<String> required() {
        return List.of("prompt");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String description = Json.str(args, "description", "subtask");
        String prompt = Json.required(args, "prompt");
        return runner.run(description, prompt);
    }
}

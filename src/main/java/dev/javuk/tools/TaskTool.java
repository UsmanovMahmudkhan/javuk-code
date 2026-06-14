package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.util.List;
import java.util.Map;

/**
 * Delegates a self-contained sub-task to a nested agent. The model uses this to
 * break a large job into focused pieces; each invocation runs its own agent loop
 * and returns a summary. An optional {@code subagent_type} picks a named agent
 * persona (its own system prompt and restricted tool set). The actual sub-agent
 * execution is supplied by a {@link Runner} wired in at startup (it captures the
 * LLM client, context, and agent registry).
 */
public final class TaskTool implements Tool {

    /** Runs the delegated prompt and returns the sub-agent's final answer. */
    public interface Runner {
        String run(String subagentType, String description, String prompt);
    }

    private final Runner runner;
    private final List<String> agentTypes;

    public TaskTool(Runner runner) {
        this(runner, List.of());
    }

    /**
     * @param agentTypes names of selectable agent personas, listed in the tool
     *                   description so the model knows what {@code subagent_type}
     *                   values are valid (may be empty)
     */
    public TaskTool(Runner runner, List<String> agentTypes) {
        this.runner = runner;
        this.agentTypes = agentTypes == null ? List.of() : List.copyOf(agentTypes);
    }

    @Override
    public String name() {
        return "Task";
    }

    @Override
    public String description() {
        String base = "Delegate a self-contained task to a sub-agent that has its own tools and "
                + "context. Use for focused, multi-step subtasks; returns the sub-agent's summary.";
        if (agentTypes.isEmpty()) {
            return base;
        }
        return base + " Optional subagent_type selects a persona: " + String.join(", ", agentTypes)
                + ". Omit it for a general-purpose sub-agent.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "description", Map.of("type", "string", "description", "Short label for the subtask"),
                "prompt", Map.of("type", "string", "description", "The full instructions for the sub-agent"),
                "subagent_type", Map.of("type", "string",
                        "description", "Optional agent persona to delegate to (see tool description)")
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
        String subagentType = Json.str(args, "subagent_type", null);
        return runner.run(subagentType, description, prompt);
    }
}

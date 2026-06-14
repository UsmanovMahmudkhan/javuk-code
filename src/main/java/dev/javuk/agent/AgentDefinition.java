package dev.javuk.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable agent persona: a system prompt plus an optional restricted tool set
 * and model override. Definitions are authored as markdown files with simple
 * frontmatter (see {@link AgentRegistry}) and selected either as the active REPL
 * persona ({@code /agents <name>}) or as a {@code Task} sub-agent type.
 *
 * @param name         identifier used to select the agent (lower-case)
 * @param description  one-line summary shown in {@code /agents} and the Task tool
 * @param tools        allowed tool names; empty means the full default tool set
 * @param model        model id override, or null to use the session model
 * @param systemPrompt the persona's system prompt (the file body)
 */
public record AgentDefinition(String name, String description,
                              List<String> tools, String model, String systemPrompt) {

    public AgentDefinition {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /** True when this agent limits the model to a specific subset of tools. */
    public boolean restrictsTools() {
        return !tools.isEmpty();
    }

    /**
     * Parses a definition from a markdown file's contents. The file may begin with
     * a {@code ---} fenced frontmatter block of {@code key: value} lines
     * ({@code name}, {@code description}, {@code tools}, {@code model}); everything
     * after it is the system prompt. Lenient: missing frontmatter just means the
     * whole text is the prompt.
     *
     * @param fallbackName name to use when no {@code name:} key is present
     */
    public static AgentDefinition parse(String fallbackName, String raw) {
        String text = raw == null ? "" : raw.strip();
        String name = fallbackName;
        String description = "";
        List<String> tools = new ArrayList<>();
        String model = null;
        String body = text;

        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end >= 0) {
                String front = text.substring(3, end);
                int bodyStart = text.indexOf('\n', end + 1);
                body = bodyStart >= 0 ? text.substring(bodyStart + 1) : "";
                for (String line : front.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon < 0) {
                        continue;
                    }
                    String key = line.substring(0, colon).strip().toLowerCase();
                    String value = line.substring(colon + 1).strip();
                    switch (key) {
                        case "name" -> {
                            if (!value.isBlank()) {
                                name = value.toLowerCase();
                            }
                        }
                        case "description" -> description = value;
                        case "model" -> model = value.isBlank() ? null : value;
                        case "tools" -> {
                            for (String t : value.split(",")) {
                                String tool = t.strip();
                                if (!tool.isEmpty()) {
                                    tools.add(tool);
                                }
                            }
                        }
                        default -> { /* ignore unknown keys */ }
                    }
                }
            }
        }
        return new AgentDefinition(name, description, tools, model, body.strip());
    }
}

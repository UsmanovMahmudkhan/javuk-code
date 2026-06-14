package dev.javuk.agent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads user-selectable {@link AgentDefinition agent personas}, modeled on
 * {@link dev.javuk.cli.CustomCommands}. Sources are merged in increasing
 * precedence so later ones override earlier by name:
 * <ol>
 *   <li>bundled built-ins (classpath {@code agents/*.md})</li>
 *   <li>user-global {@code ~/.config/javuk/agents/*.md}</li>
 *   <li>project {@code <workingDir>/.javuk/agents/*.md}</li>
 * </ol>
 * Each file {@code foo.md} becomes the {@code foo} agent. Malformed or unreadable
 * files are skipped silently.
 */
public final class AgentRegistry {

    /** Built-in agents shipped as resources; keep in sync with src/main/resources/agents. */
    private static final List<String> BUILT_IN =
            List.of("code-reviewer", "planner", "test-writer", "explorer");

    private final Map<String, AgentDefinition> agents = new LinkedHashMap<>();

    public AgentRegistry(Path workingDir) {
        loadBuiltIns();
        loadDir(Path.of(System.getProperty("user.home"), ".config", "javuk", "agents"));
        if (workingDir != null) {
            loadDir(workingDir.resolve(".javuk").resolve("agents"));
        }
    }

    private void loadBuiltIns() {
        for (String name : BUILT_IN) {
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("agents/" + name + ".md")) {
                if (in == null) {
                    continue;
                }
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                AgentDefinition def = AgentDefinition.parse(name, raw);
                agents.put(def.name(), def);
            } catch (Exception ignored) {
                // a missing/broken built-in resource is non-fatal
            }
        }
    }

    private void loadDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            String fallback = p.getFileName().toString()
                                    .replaceFirst("\\.md$", "").toLowerCase();
                            AgentDefinition def = AgentDefinition.parse(fallback, Files.readString(p));
                            agents.put(def.name(), def);
                        } catch (Exception ignored) {
                            // unreadable agent file — skip
                        }
                    });
        } catch (Exception ignored) {
            // no agents dir — fine
        }
    }

    public boolean has(String name) {
        return name != null && agents.containsKey(name.toLowerCase());
    }

    /** The agent with the given name, or null if unknown. */
    public AgentDefinition get(String name) {
        return name == null ? null : agents.get(name.toLowerCase());
    }

    public Set<String> names() {
        return agents.keySet();
    }

    public List<AgentDefinition> all() {
        return List.copyOf(agents.values());
    }

    public boolean isEmpty() {
        return agents.isEmpty();
    }
}

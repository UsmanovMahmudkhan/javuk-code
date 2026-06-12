package dev.javuk.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * User-defined slash commands loaded from {@code .javuk/commands/*.md}. Each
 * file {@code foo.md} becomes {@code /foo}; its contents are a prompt template
 * where {@code $ARGUMENTS} is replaced with whatever the user typed after the
 * command. Invoking the command sends the expanded template as a prompt.
 */
public final class CustomCommands {

    private final Map<String, Path> commands = new LinkedHashMap<>();

    public CustomCommands(Path workingDir) {
        Path dir = workingDir.resolve(".javuk").resolve("commands");
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> commands.put(
                            p.getFileName().toString().replaceFirst("\\.md$", "").toLowerCase(), p));
        } catch (Exception ignored) {
            // no custom commands — fine
        }
    }

    public boolean has(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    public java.util.Set<String> names() {
        return commands.keySet();
    }

    /** Expands the named command's template with {@code $ARGUMENTS}, or null if unknown. */
    public String expand(String name, String arguments) {
        Path file = commands.get(name.toLowerCase());
        if (file == null) {
            return null;
        }
        try {
            String template = Files.readString(file);
            return template.replace("$ARGUMENTS", arguments == null ? "" : arguments);
        } catch (Exception e) {
            return null;
        }
    }
}

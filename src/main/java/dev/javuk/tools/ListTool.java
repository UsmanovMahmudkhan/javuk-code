package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Lists the entries of a directory, marking sub-directories with a trailing slash. */
public final class ListTool implements Tool {

    @Override
    public String name() {
        return "List";
    }

    @Override
    public String description() {
        return "List the contents of a directory. Directories are shown with a trailing slash.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "path", Map.of("type", "string", "description", "Directory to list (default: working directory)")
        );
    }

    @Override
    public List<String> required() {
        return List.of();
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Path dir = ctx.resolveConfined(Json.str(args, "path", "."));
        if (!Files.exists(dir)) {
            return "Error: path not found: " + dir;
        }
        if (!Files.isDirectory(dir)) {
            return "Error: not a directory: " + dir;
        }

        List<String> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> entries.add(p.getFileName() + (Files.isDirectory(p) ? "/" : "")));
        }
        if (entries.isEmpty()) {
            return "(empty directory)";
        }
        return String.join("\n", entries);
    }
}

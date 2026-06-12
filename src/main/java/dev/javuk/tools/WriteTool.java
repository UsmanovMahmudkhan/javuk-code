package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.ui.DiffRenderer;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Writes content to a file, creating parent directories as needed. */
public final class WriteTool implements Tool {

    @Override
    public String name() {
        return "Write";
    }

    @Override
    public String description() {
        return "Write content to a file, overwriting it if it already exists. "
                + "Creates parent directories automatically.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "file_path", Map.of("type", "string", "description", "Path of the file to write"),
                "content", Map.of("type", "string", "description", "The content to write")
        );
    }

    @Override
    public List<String> required() {
        return List.of("file_path", "content");
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String preview(JsonNode args, ToolContext ctx) {
        try {
            Path path = ctx.resolve(Json.required(args, "file_path"));
            String before = Files.exists(path) ? Files.readString(path) : "";
            String after = Json.str(args, "content", "");
            return "write " + path + "\n" + DiffRenderer.diff(before, after);
        } catch (Exception e) {
            return "write " + Json.str(args, "file_path", "?");
        }
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Path path = ctx.resolve(Json.required(args, "file_path"));
        String content = Json.required(args, "content");

        boolean existed = Files.exists(path);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content);

        long lines = content.isEmpty() ? 0 : content.lines().count();
        return (existed ? "Updated " : "Created ") + path + " (" + lines + " lines)";
    }
}

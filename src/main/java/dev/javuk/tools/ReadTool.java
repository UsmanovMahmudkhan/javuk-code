package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Reads a file and returns its contents with {@code cat -n} style line numbers.
 * Supports {@code offset}/{@code limit} so the model can page through large files.
 */
public final class ReadTool implements Tool {

    private static final int MAX_LINE_LENGTH = 2000;

    @Override
    public String name() {
        return "Read";
    }

    @Override
    public String description() {
        return "Read a file from the filesystem. Returns contents with line numbers. "
                + "Use offset/limit to read a slice of large files.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "file_path", Map.of("type", "string", "description", "Path to the file to read"),
                "offset", Map.of("type", "integer", "description", "1-based line number to start from (optional)"),
                "limit", Map.of("type", "integer", "description", "Maximum number of lines to read (optional)")
        );
    }

    @Override
    public List<String> required() {
        return List.of("file_path");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Path path = ctx.resolveConfined(Json.required(args, "file_path"));
        if (!Files.exists(path)) {
            return "Error: file not found: " + path;
        }
        if (Files.isDirectory(path)) {
            return "Error: path is a directory, not a file: " + path;
        }

        List<String> lines = Files.readAllLines(path);
        int offset = Math.max(1, Json.intOr(args, "offset", 1));
        int limit = Json.intOr(args, "limit", Integer.MAX_VALUE);

        StringBuilder sb = new StringBuilder();
        int end = Math.min(lines.size(), offset - 1 + limit);
        for (int i = offset - 1; i < end; i++) {
            String line = lines.get(i);
            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH) + "… [truncated]";
            }
            sb.append(String.format("%6d\t%s%n", i + 1, line));
        }
        if (sb.isEmpty()) {
            return "(file is empty or the requested range has no lines)";
        }
        return sb.toString();
    }
}

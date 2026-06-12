package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.ui.DiffRenderer;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a sequence of {@code {old_string, new_string, replace_all}} edits to a
 * single file in order, atomically: if any edit fails to match, nothing is
 * written and the error is returned.
 */
public final class MultiEditTool implements Tool {

    @Override
    public String name() {
        return "MultiEdit";
    }

    @Override
    public String description() {
        return "Apply multiple Edit operations to one file in sequence. All edits must "
                + "succeed or none are applied.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "file_path", Map.of("type", "string", "description", "Path of the file to edit"),
                "edits", Map.of(
                        "type", "array",
                        "description", "Ordered list of edits to apply",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "old_string", Map.of("type", "string"),
                                        "new_string", Map.of("type", "string"),
                                        "replace_all", Map.of("type", "boolean")),
                                "required", List.of("old_string", "new_string"))
                )
        );
    }

    @Override
    public List<String> required() {
        return List.of("file_path", "edits");
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String preview(JsonNode args, ToolContext ctx) {
        try {
            Path path = ctx.resolve(Json.required(args, "file_path"));
            if (!Files.exists(path)) {
                return "multi-edit " + path + " (file not found)";
            }
            String before = Files.readString(path);
            JsonNode edits = args.get("edits");
            String after = before;
            if (edits != null && edits.isArray()) {
                for (JsonNode e : edits) {
                    String oldStr = Json.str(e, "old_string", "");
                    if (!after.contains(oldStr)) {
                        return "multi-edit " + path + " (an edit does not match)";
                    }
                    after = applyOne(after, oldStr, Json.str(e, "new_string", ""),
                            e.path("replace_all").asBoolean(false));
                }
            }
            return "multi-edit " + path + "\n" + DiffRenderer.diff(before, after);
        } catch (Exception e) {
            return "multi-edit " + Json.str(args, "file_path", "?");
        }
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Path path = ctx.resolve(Json.required(args, "file_path"));
        if (!Files.exists(path)) {
            return "Error: file not found: " + path;
        }
        JsonNode edits = args.get("edits");
        if (edits == null || !edits.isArray() || edits.isEmpty()) {
            return "Error: 'edits' must be a non-empty array";
        }

        String content = Files.readString(path);
        int applied = 0;
        for (int i = 0; i < edits.size(); i++) {
            JsonNode e = edits.get(i);
            String oldStr = Json.required(e, "old_string");
            String newStr = Json.required(e, "new_string");
            boolean replaceAll = e.path("replace_all").asBoolean(false);

            if (!content.contains(oldStr)) {
                return "Error: edit #" + (i + 1) + " old_string not found; no changes written.";
            }
            content = applyOne(content, oldStr, newStr, replaceAll);
            applied++;
        }

        Files.writeString(path, content);
        return "Applied " + applied + " edits to " + path;
    }

    private static String applyOne(String content, String oldStr, String newStr, boolean replaceAll) {
        return replaceAll
                ? content.replace(oldStr, newStr)
                : content.replaceFirst(Pattern.quote(oldStr), Matcher.quoteReplacement(newStr));
    }
}

package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.ui.DiffRenderer;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Replaces an exact string in a file. By default {@code old_string} must occur
 * exactly once (so edits are unambiguous); set {@code replace_all} to replace
 * every occurrence.
 */
public final class EditTool implements Tool {

    @Override
    public String name() {
        return "Edit";
    }

    @Override
    public String description() {
        return "Replace an exact substring in a file. old_string must be unique unless "
                + "replace_all is true. Fails if old_string is not found.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "file_path", Map.of("type", "string", "description", "Path of the file to edit"),
                "old_string", Map.of("type", "string", "description", "Exact text to replace"),
                "new_string", Map.of("type", "string", "description", "Replacement text"),
                "replace_all", Map.of("type", "boolean", "description", "Replace all occurrences (default false)")
        );
    }

    @Override
    public List<String> required() {
        return List.of("file_path", "old_string", "new_string");
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
                return "edit " + path + " (file not found)";
            }
            String content = Files.readString(path);
            String oldStr = Json.required(args, "old_string");
            String newStr = Json.required(args, "new_string");
            boolean replaceAll = args.path("replace_all").asBoolean(false);
            if (!content.contains(oldStr)) {
                return "edit " + path + " (old_string not found)";
            }
            String updated = applyReplacement(content, oldStr, newStr, replaceAll);
            return "edit " + path + "\n" + DiffRenderer.diff(content, updated);
        } catch (Exception e) {
            return "edit " + Json.str(args, "file_path", "?");
        }
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Path path = ctx.resolve(Json.required(args, "file_path"));
        if (!Files.exists(path)) {
            return "Error: file not found: " + path;
        }
        String oldStr = Json.required(args, "old_string");
        String newStr = Json.required(args, "new_string");
        boolean replaceAll = args.path("replace_all").asBoolean(false);

        String content = Files.readString(path);
        int count = countOccurrences(content, oldStr);
        if (count == 0) {
            return "Error: old_string not found in " + path;
        }
        if (count > 1 && !replaceAll) {
            return "Error: old_string occurs " + count + " times in " + path
                    + ". Make it unique or pass replace_all=true.";
        }

        String updated = applyReplacement(content, oldStr, newStr, replaceAll);
        Files.writeString(path, updated);

        return "Edited " + path + " (" + (replaceAll ? count + " replacements" : "1 replacement") + ")";
    }

    private static String applyReplacement(String content, String oldStr, String newStr, boolean replaceAll) {
        return replaceAll
                ? content.replace(oldStr, newStr)
                : content.replaceFirst(java.util.regex.Pattern.quote(oldStr),
                        java.util.regex.Matcher.quoteReplacement(newStr));
    }

    private static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}

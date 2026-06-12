package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Searches file contents for a regular expression and reports matches as
 * {@code path:line: text}. Honors an optional {@code glob} filename filter and
 * case-insensitive mode; skips common noise directories and binary-looking files.
 */
public final class GrepTool implements Tool {

    private static final int MAX_MATCHES = 200;

    @Override
    public String name() {
        return "Grep";
    }

    @Override
    public String description() {
        return "Search file contents with a regular expression. Returns matching lines as "
                + "path:line: text. Optional glob filter and case-insensitive mode.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "pattern", Map.of("type", "string", "description", "Regular expression to search for"),
                "path", Map.of("type", "string", "description", "Base directory to search (optional)"),
                "glob", Map.of("type", "string", "description", "Only search files whose name matches this glob (optional)"),
                "ignore_case", Map.of("type", "boolean", "description", "Case-insensitive matching (optional)")
        );
    }

    @Override
    public List<String> required() {
        return List.of("pattern");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        String patternStr = Json.required(args, "pattern");
        Path base = ctx.resolve(Json.str(args, "path", "."));
        String glob = Json.str(args, "glob", null);
        boolean ignoreCase = args.path("ignore_case").asBoolean(false);

        Pattern pattern = Pattern.compile(patternStr, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        var nameMatcher = glob == null ? null
                : java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + glob);

        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(base.relativize(p)))
                    .filter(p -> nameMatcher == null || nameMatcher.matches(p.getFileName()))
                    .forEach(p -> {
                        if (count[0] >= MAX_MATCHES) {
                            return;
                        }
                        searchFile(p, pattern, sb, count);
                    });
        }

        if (count[0] == 0) {
            return "No matches for: " + patternStr;
        }
        if (count[0] >= MAX_MATCHES) {
            sb.append("[results capped at ").append(MAX_MATCHES).append(" matches]\n");
        }
        return sb.toString();
    }

    private void searchFile(Path p, Pattern pattern, StringBuilder sb, int[] count) {
        List<String> lines;
        try {
            lines = Files.readAllLines(p);
        } catch (Exception e) {
            return; // unreadable / binary file — skip silently
        }
        for (int i = 0; i < lines.size() && count[0] < MAX_MATCHES; i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                sb.append(p).append(':').append(i + 1).append(": ").append(lines.get(i).strip()).append('\n');
                count[0]++;
            }
        }
    }

    private static boolean isIgnored(Path relative) {
        for (Path part : relative) {
            String s = part.toString();
            if (s.equals(".git") || s.equals("target") || s.equals("node_modules") || s.equals(".idea")) {
                return true;
            }
        }
        return false;
    }
}

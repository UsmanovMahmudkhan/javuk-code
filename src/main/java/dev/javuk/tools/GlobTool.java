package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds files matching a glob pattern (e.g. {@code **&#47;*.java}) under a base
 * directory, skipping common noise dirs like {@code .git}, {@code target},
 * {@code node_modules}. Results are returned newest-first.
 */
public final class GlobTool implements Tool {

    private static final int MAX_RESULTS = 200;

    @Override
    public String name() {
        return "Glob";
    }

    @Override
    public String description() {
        return "Find files matching a glob pattern (e.g. **/*.java). Returns matching paths, "
                + "most recently modified first.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "pattern", Map.of("type", "string", "description", "Glob pattern, e.g. src/**/*.java"),
                "path", Map.of("type", "string", "description", "Base directory to search (optional)")
        );
    }

    @Override
    public List<String> required() {
        return List.of("pattern");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        String pattern = Json.required(args, "pattern");
        Path base = ctx.resolveConfined(Json.str(args, "path", "."));
        if (!Files.isDirectory(base)) {
            return "Error: not a directory: " + base;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> walk = Files.walk(base)) {
            List<Path> matches = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(base.relativize(p)))
                    .filter(p -> matcher.matches(base.relativize(p)) || matcher.matches(p.getFileName()))
                    .sorted((a, b) -> Long.compare(lastModified(b), lastModified(a)))
                    .limit(MAX_RESULTS)
                    .toList();

            if (matches.isEmpty()) {
                return "No files matching: " + pattern;
            }
            StringBuilder sb = new StringBuilder();
            for (Path p : matches) {
                sb.append(p).append('\n');
            }
            if (matches.size() == MAX_RESULTS) {
                sb.append("[results capped at ").append(MAX_RESULTS).append("]\n");
            }
            return sb.toString();
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

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}

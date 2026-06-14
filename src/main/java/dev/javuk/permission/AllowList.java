package dev.javuk.permission;

import dev.javuk.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A persistent set of "always allow" patterns. Stored as JSON at
 * {@code ~/.config/javuk/allowlist.json}.
 *
 * <p>Two pattern forms, both <b>anchored</b> (never a loose substring, so a
 * pattern can't accidentally match text buried in the middle of a command):
 * <ul>
 *   <li>{@code Tool} — exact tool name: auto-approves every action of that tool
 *       (e.g. {@code Read}).</li>
 *   <li>{@code Tool: prefix} — the action's preview must <em>start with</em>
 *       {@code prefix} (e.g. {@code Bash: git} approves {@code git status} but not
 *       {@code rm -rf x # git}). A leading {@code run: } verb in the preview is
 *       ignored so {@code Bash: git} works as expected; a trailing {@code *} in
 *       the pattern is allowed and ignored.</li>
 * </ul>
 */
public final class AllowList {

    private final Path file;
    private final Set<String> patterns = new LinkedHashSet<>();

    public AllowList() {
        this(defaultFile());
    }

    public AllowList(Path file) {
        this.file = file;
        load();
    }

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".config", "javuk", "allowlist.json");
    }

    public boolean allows(String toolName, String preview) {
        for (String pattern : patterns) {
            if (matches(pattern, toolName, preview)) {
                return true;
            }
        }
        return false;
    }

    /** Anchored match: whole-tool ({@code Tool}) or tool-scoped prefix ({@code Tool: prefix}). */
    private static boolean matches(String pattern, String toolName, String preview) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.equals(toolName)) {
            return true; // whole-tool allow
        }
        int sep = pattern.indexOf(": ");
        if (sep <= 0) {
            return false; // not tool-scoped — anchoring requires "Tool: prefix"
        }
        if (!pattern.substring(0, sep).equals(toolName)) {
            return false;
        }
        String prefix = stripTrailingStar(pattern.substring(sep + 2).strip());
        String action = preview == null ? "" : preview;
        return action.startsWith(prefix) || stripLeadingVerb(action).startsWith(prefix);
    }

    private static String stripTrailingStar(String s) {
        return s.endsWith("*") ? s.substring(0, s.length() - 1) : s;
    }

    /** Drops a leading {@code "run: "} so {@code Bash: git} matches {@code run: git status}. */
    private static String stripLeadingVerb(String preview) {
        return preview.startsWith("run: ") ? preview.substring("run: ".length()) : preview;
    }

    public void add(String pattern) {
        if (pattern != null && !pattern.isBlank()) {
            patterns.add(pattern.strip());
            save();
        }
    }

    public Set<String> patterns() {
        return Set.copyOf(patterns);
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> stored = Json.MAPPER.readValue(Files.readString(file), List.class);
            patterns.addAll(stored);
        } catch (Exception ignored) {
            // corrupt allowlist — start empty
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(List.copyOf(patterns)));
        } catch (IOException ignored) {
            // best-effort persistence
        }
    }
}

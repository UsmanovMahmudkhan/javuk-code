package dev.javuk.permission;

import dev.javuk.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A persistent set of "always allow" patterns. A mutating action is auto-approved
 * (no prompt) when any pattern is a substring of {@code "<Tool>: <preview>"}, or
 * equals the tool name. Stored as JSON at {@code ~/.config/javuk/allowlist.json}.
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
        if (patterns.contains(toolName)) {
            return true;
        }
        String hay = toolName + ": " + preview;
        for (String p : patterns) {
            if (!p.isBlank() && hay.contains(p)) {
                return true;
            }
        }
        return false;
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

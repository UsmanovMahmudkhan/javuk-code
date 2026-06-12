package dev.javuk.tools;

import dev.javuk.hooks.Hooks;
import dev.javuk.permission.PermissionService;

import java.nio.file.Path;

/**
 * Ambient state handed to every {@link Tool#execute}. Carries the working
 * directory the agent operates in, the permission gate that mutating tools must
 * clear, and any user-defined {@link Hooks} run around tool execution.
 */
public final class ToolContext {

    private final Path workingDir;
    private final PermissionService permissions;
    private final Hooks hooks;

    public ToolContext(Path workingDir, PermissionService permissions) {
        this(workingDir, permissions, Hooks.none());
    }

    public ToolContext(Path workingDir, PermissionService permissions, Hooks hooks) {
        this.workingDir = workingDir;
        this.permissions = permissions;
        this.hooks = hooks;
    }

    public Path workingDir() {
        return workingDir;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public Hooks hooks() {
        return hooks;
    }

    /** Resolves a possibly-relative path against the working directory. */
    public Path resolve(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : workingDir.resolve(p).normalize();
    }
}

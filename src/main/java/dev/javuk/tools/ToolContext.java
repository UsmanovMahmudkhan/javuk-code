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
    private final boolean allowOutsideWorkspace;

    public ToolContext(Path workingDir, PermissionService permissions) {
        this(workingDir, permissions, Hooks.none());
    }

    public ToolContext(Path workingDir, PermissionService permissions, Hooks hooks) {
        this(workingDir, permissions, hooks, false);
    }

    public ToolContext(Path workingDir, PermissionService permissions, Hooks hooks,
                       boolean allowOutsideWorkspace) {
        this.workingDir = workingDir;
        this.permissions = permissions;
        this.hooks = hooks;
        this.allowOutsideWorkspace = allowOutsideWorkspace;
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

    /** Whether file/search tools may operate outside the working directory. */
    public boolean allowOutsideWorkspace() {
        return allowOutsideWorkspace;
    }

    /** Resolves a possibly-relative path against the working directory. */
    public Path resolve(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : workingDir.resolve(p).normalize();
    }

    /**
     * Like {@link #resolve} but, unless {@code allowOutsideWorkspace} is set,
     * verifies the resolved path stays within the working directory. Prevents the
     * agent from reading or writing arbitrary files (e.g. {@code ~/.ssh},
     * {@code ../../etc/passwd}) via absolute paths or {@code ..} traversal.
     *
     * @throws SecurityException if the path escapes the workspace
     */
    public Path resolveConfined(String path) {
        Path resolved = resolve(path);
        if (allowOutsideWorkspace) {
            return resolved;
        }
        Path base = workingDir.toAbsolutePath().normalize();
        Path target = resolved.toAbsolutePath().normalize();
        if (!target.startsWith(base)) {
            throw new SecurityException("path escapes the workspace: " + path
                    + " (set workspace.allowOutside or pass --allow-outside-workspace to override)");
        }
        return target;
    }
}

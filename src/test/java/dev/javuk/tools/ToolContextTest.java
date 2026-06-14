package dev.javuk.tools;

import dev.javuk.hooks.Hooks;
import dev.javuk.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Workspace-confinement tests for {@link ToolContext#resolveConfined}. */
class ToolContextTest {

    private ToolContext confined(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    private ToolContext unconfined(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll(), Hooks.none(), true);
    }

    @Test
    void allowsPathsInsideWorkspace(@TempDir Path dir) {
        Path p = confined(dir).resolveConfined("sub/file.txt");
        assertTrue(p.startsWith(dir.toAbsolutePath().normalize()));
    }

    @Test
    void rejectsParentTraversal(@TempDir Path dir) {
        assertThrows(SecurityException.class,
                () -> confined(dir).resolveConfined("../../etc/passwd"));
    }

    @Test
    void rejectsAbsoluteOutsidePath(@TempDir Path dir) {
        assertThrows(SecurityException.class,
                () -> confined(dir).resolveConfined("/etc/passwd"));
    }

    @Test
    void optOutAllowsOutsidePaths(@TempDir Path dir) {
        Path p = unconfined(dir).resolveConfined("/etc/passwd");
        assertEquals(Path.of("/etc/passwd"), p);
    }
}

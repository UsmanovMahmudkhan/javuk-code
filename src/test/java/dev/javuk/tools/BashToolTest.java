package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BashToolTest {

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void capturesStdout(@TempDir Path dir) throws Exception {
        String out = new BashTool().execute(Json.parse("{\"command\":\"echo hi\"}"), ctx(dir));
        assertTrue(out.contains("hi"));
    }

    @Test
    void runsInWorkingDirectory(@TempDir Path dir) throws Exception {
        String out = new BashTool().execute(Json.parse("{\"command\":\"pwd\"}"), ctx(dir));
        // macOS may prefix /private for temp dirs; match on the dir name.
        assertTrue(out.contains(dir.getFileName().toString()));
    }

    @Test
    void reportsNonZeroExit(@TempDir Path dir) throws Exception {
        String out = new BashTool().execute(Json.parse("{\"command\":\"exit 3\"}"), ctx(dir));
        assertTrue(out.contains("exit code 3"));
    }
}

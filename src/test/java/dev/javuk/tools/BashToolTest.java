package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

    @Test
    void timeoutKillsHangingCommandPromptly(@TempDir Path dir) {
        // sleep 30 with a 1s timeout must return in ~1s, not 30s.
        String out = assertTimeoutPreemptively(Duration.ofSeconds(8), () ->
                new BashTool().execute(Json.parse("{\"command\":\"sleep 30\",\"timeout\":1}"), ctx(dir)));
        assertTrue(out.contains("timed out after 1s"), () -> "expected timeout marker, got: " + out);
    }

    @Test
    void boundsLargeOutputWithoutHanging(@TempDir Path dir) {
        // 100 KB of output far exceeds the 30 KB cap; must truncate and not hang/OOM.
        String out = assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                new BashTool().execute(
                        Json.parse("{\"command\":\"yes hello | head -c 100000\"}"), ctx(dir)));
        assertTrue(out.contains("output truncated"), () -> "expected truncation note, got prefix: "
                + out.substring(0, Math.min(80, out.length())));
        assertTrue(out.length() < 40_000, "captured output should be bounded near the cap");
        assertFalse(out.contains("timed out"), "a finite command should not time out");
    }
}

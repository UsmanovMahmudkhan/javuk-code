package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadToolTest {

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void readsFileWithLineNumbers(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "first\nsecond\nthird\n");

        String out = new ReadTool().execute(Json.parse("{\"file_path\":\"a.txt\"}"), ctx(dir));

        assertTrue(out.contains("     1\tfirst"));
        assertTrue(out.contains("     2\tsecond"));
        assertTrue(out.contains("     3\tthird"));
    }

    @Test
    void respectsOffsetAndLimit(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "l1\nl2\nl3\nl4\nl5\n");

        String out = new ReadTool().execute(
                Json.parse("{\"file_path\":\"a.txt\",\"offset\":2,\"limit\":2}"), ctx(dir));

        assertFalse(out.contains("l1"));
        assertTrue(out.contains("     2\tl2"));
        assertTrue(out.contains("     3\tl3"));
        assertFalse(out.contains("l4"));
    }

    @Test
    void reportsMissingFile(@TempDir Path dir) throws Exception {
        String out = new ReadTool().execute(Json.parse("{\"file_path\":\"nope.txt\"}"), ctx(dir));
        assertTrue(out.toLowerCase().contains("not found"));
    }
}

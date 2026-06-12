package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteToolTest {

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void createsFileAndParentDirs(@TempDir Path dir) throws Exception {
        String out = new WriteTool().execute(
                Json.parse("{\"file_path\":\"nested/dir/x.txt\",\"content\":\"hello\"}"), ctx(dir));

        Path written = dir.resolve("nested/dir/x.txt");
        assertTrue(Files.exists(written));
        assertEquals("hello", Files.readString(written));
        assertTrue(out.startsWith("Created"));
    }

    @Test
    void reportsUpdateOnOverwrite(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("x.txt"), "old");
        String out = new WriteTool().execute(
                Json.parse("{\"file_path\":\"x.txt\",\"content\":\"new\"}"), ctx(dir));

        assertEquals("new", Files.readString(dir.resolve("x.txt")));
        assertTrue(out.startsWith("Updated"));
    }
}

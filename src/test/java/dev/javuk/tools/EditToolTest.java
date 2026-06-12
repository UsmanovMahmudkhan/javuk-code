package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditToolTest {

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void replacesUniqueString(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "hello world");
        String out = new EditTool().execute(
                Json.parse("{\"file_path\":\"a.txt\",\"old_string\":\"world\",\"new_string\":\"Javuk\"}"),
                ctx(dir));
        assertEquals("hello Javuk", Files.readString(dir.resolve("a.txt")));
        assertTrue(out.contains("1 replacement"));
    }

    @Test
    void rejectsAmbiguousEdit(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "x x x");
        String out = new EditTool().execute(
                Json.parse("{\"file_path\":\"a.txt\",\"old_string\":\"x\",\"new_string\":\"y\"}"),
                ctx(dir));
        assertTrue(out.contains("occurs 3 times"));
        assertEquals("x x x", Files.readString(dir.resolve("a.txt")));
    }

    @Test
    void replaceAllReplacesEveryOccurrence(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "x x x");
        new EditTool().execute(
                Json.parse("{\"file_path\":\"a.txt\",\"old_string\":\"x\",\"new_string\":\"y\",\"replace_all\":true}"),
                ctx(dir));
        assertEquals("y y y", Files.readString(dir.resolve("a.txt")));
    }

    @Test
    void reportsMissingString(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "hello");
        String out = new EditTool().execute(
                Json.parse("{\"file_path\":\"a.txt\",\"old_string\":\"zzz\",\"new_string\":\"q\"}"),
                ctx(dir));
        assertTrue(out.contains("not found"));
    }
}

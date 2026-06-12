package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers Glob, Grep, List, MultiEdit and TodoWrite. */
class SearchToolsTest {

    private ToolContext ctx(Path dir) {
        return new ToolContext(dir, PermissionService.allowAll());
    }

    @Test
    void globFindsByExtension(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/A.java"), "class A {}");
        Files.writeString(dir.resolve("src/B.txt"), "text");

        String out = new GlobTool().execute(Json.parse("{\"pattern\":\"**/*.java\"}"), ctx(dir));
        assertTrue(out.contains("A.java"));
        assertFalse(out.contains("B.txt"));
    }

    @Test
    void grepFindsMatchingLines(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "alpha\nNEEDLE here\nbeta\n");
        String out = new GrepTool().execute(Json.parse("{\"pattern\":\"NEEDLE\"}"), ctx(dir));
        assertTrue(out.contains("a.txt:2"));
        assertTrue(out.contains("NEEDLE here"));
    }

    @Test
    void grepIgnoreCase(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "Hello World\n");
        String out = new GrepTool().execute(
                Json.parse("{\"pattern\":\"hello\",\"ignore_case\":true}"), ctx(dir));
        assertTrue(out.contains("a.txt:1"));
    }

    @Test
    void listShowsDirsWithSlash(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("file.txt"), "x");
        String out = new ListTool().execute(Json.parse("{}"), ctx(dir));
        assertTrue(out.contains("sub/"));
        assertTrue(out.contains("file.txt"));
    }

    @Test
    void multiEditAppliesAllOrNothing(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "one two three");
        new MultiEditTool().execute(Json.parse(
                "{\"file_path\":\"a.txt\",\"edits\":["
                        + "{\"old_string\":\"one\",\"new_string\":\"1\"},"
                        + "{\"old_string\":\"three\",\"new_string\":\"3\"}]}"), ctx(dir));
        assertTrue(Files.readString(dir.resolve("a.txt")).equals("1 two 3"));
    }

    @Test
    void multiEditRollsBackOnMissingMatch(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "one two");
        String out = new MultiEditTool().execute(Json.parse(
                "{\"file_path\":\"a.txt\",\"edits\":["
                        + "{\"old_string\":\"one\",\"new_string\":\"1\"},"
                        + "{\"old_string\":\"zzz\",\"new_string\":\"9\"}]}"), ctx(dir));
        assertTrue(out.contains("not found"));
        // First edit must not have been written because the second failed.
        assertTrue(Files.readString(dir.resolve("a.txt")).equals("one two"));
    }

    @Test
    void todoWriteRendersStatuses(@TempDir Path dir) throws Exception {
        String out = new TodoWriteTool().execute(Json.parse(
                "{\"todos\":[{\"content\":\"do x\",\"status\":\"completed\"},"
                        + "{\"content\":\"do y\",\"status\":\"in_progress\"}]}"), ctx(dir));
        assertTrue(out.contains("[x] do x"));
        assertTrue(out.contains("[~] do y"));
    }

    @Test
    void htmlToTextStripsTags() {
        String text = WebFetchTool.htmlToText("<html><body><p>Hi <b>there</b></p><script>x()</script></body></html>");
        assertTrue(text.contains("Hi"));
        assertTrue(text.contains("there"));
        assertFalse(text.contains("x()"));
        assertFalse(text.contains("<"));
    }
}

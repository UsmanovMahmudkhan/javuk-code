package dev.javuk;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.cli.CustomCommands;
import dev.javuk.cli.Mentions;
import dev.javuk.hooks.Hooks;
import dev.javuk.permission.AllowList;
import dev.javuk.tools.TaskTool;
import dev.javuk.tools.ToolContext;
import dev.javuk.permission.PermissionService;
import dev.javuk.ui.DiffRenderer;
import dev.javuk.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedFeaturesTest {

    @Test
    void diffShowsAddedAndRemoved() {
        String out = DiffRenderer.diff("a\nb\nc", "a\nB\nc");
        assertTrue(out.contains("1 added"));
        assertTrue(out.contains("1 removed"));
        assertTrue(out.contains("+ B"));
        assertTrue(out.contains("- b"));
    }

    @Test
    void mentionsExpandReadableFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("note.txt"), "hello world");
        String expanded = Mentions.expand("look at @note.txt please", dir);
        assertTrue(expanded.contains("Contents of note.txt"));
        assertTrue(expanded.contains("hello world"));
    }

    @Test
    void mentionsLeaveTextWithoutFilesAlone(@TempDir Path dir) {
        String input = "no mentions here";
        assertEquals(input, Mentions.expand(input, dir));
    }

    @Test
    void customCommandsLoadAndExpand(@TempDir Path dir) throws Exception {
        Path cmds = dir.resolve(".javuk/commands");
        Files.createDirectories(cmds);
        Files.writeString(cmds.resolve("review.md"), "Review $ARGUMENTS for bugs.");

        CustomCommands cc = new CustomCommands(dir);
        assertTrue(cc.has("review"));
        assertEquals("Review Parser.java for bugs.", cc.expand("review", "Parser.java"));
        assertNull(cc.expand("missing", ""));
    }

    @Test
    void allowListMatchingAndPersistence(@TempDir Path dir) {
        Path file = dir.resolve("allow.json");
        AllowList list = new AllowList(file);
        list.add("Read");                 // whole-tool allow
        list.add("Bash: git");            // tool-scoped prefix (leading "run: " is ignored)

        assertTrue(list.allows("Read", "any preview"));
        assertTrue(list.allows("Bash", "run: git status"));
        assertFalse(list.allows("Write", "write /tmp/x.txt"));

        // Anchored, not a loose substring: a match buried mid-command is rejected.
        assertFalse(list.allows("Bash", "run: rm -rf x # git"));
        // Tool scoping: a Bash pattern must not leak into another tool.
        assertFalse(list.allows("Write", "git config"));

        // Reloads from disk.
        AllowList reloaded = new AllowList(file);
        assertTrue(reloaded.allows("Read", "x"));
        assertTrue(reloaded.allows("Bash", "run: git status"));
    }

    @Test
    void taskToolDelegatesToRunner(@TempDir Path dir) throws Exception {
        TaskTool tool = new TaskTool((type, desc, prompt) -> "handled: " + prompt);
        JsonNode args = Json.parse("{\"description\":\"x\",\"prompt\":\"do the thing\"}");
        String result = tool.execute(args, new ToolContext(dir, PermissionService.allowAll()));
        assertEquals("handled: do the thing", result);
    }

    @Test
    void taskToolPassesSubagentType(@TempDir Path dir) throws Exception {
        TaskTool tool = new TaskTool((type, desc, prompt) -> "type=" + type);
        JsonNode args = Json.parse(
                "{\"prompt\":\"go\",\"subagent_type\":\"explorer\"}");
        String result = tool.execute(args, new ToolContext(dir, PermissionService.allowAll()));
        assertEquals("type=explorer", result);
    }

    @Test
    void preHookBlocksOnNonZeroExit(@TempDir Path dir) {
        Hooks block = new Hooks(java.util.List.of("echo nope; exit 1"), java.util.List.of());
        String denial = block.runPre("Bash", "{}", dir);
        assertNotNull(denial);
        assertTrue(denial.contains("nope"));

        Hooks ok = new Hooks(java.util.List.of("exit 0"), java.util.List.of());
        assertNull(ok.runPre("Bash", "{}", dir));
    }

    @Test
    void postHookRuns(@TempDir Path dir) throws Exception {
        Path marker = dir.resolve("ran.txt");
        Hooks hooks = new Hooks(java.util.List.of(), java.util.List.of("echo done > ran.txt"));
        hooks.runPost("Write", "{}", dir);
        assertTrue(Files.exists(marker));
    }
}

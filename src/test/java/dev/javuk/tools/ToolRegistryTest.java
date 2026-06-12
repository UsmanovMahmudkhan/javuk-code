package dev.javuk.tools;

import dev.javuk.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    private ToolRegistry registry() {
        return new ToolRegistry()
                .register(new ReadTool())
                .register(new WriteTool())
                .register(new BashTool());
    }

    @Test
    void unknownToolReturnsError(@TempDir Path dir) {
        String out = registry().dispatch("Nope", "{}",
                new ToolContext(dir, PermissionService.allowAll()));
        assertTrue(out.contains("unknown tool"));
    }

    @Test
    void readOnlyModeBlocksMutatingTools(@TempDir Path dir) {
        ToolContext ctx = new ToolContext(dir, PermissionService.readOnly());
        String out = registry().dispatch("Write",
                "{\"file_path\":\"blocked.txt\",\"content\":\"x\"}", ctx);

        assertTrue(out.contains("Permission denied"));
        assertFalse(Files.exists(dir.resolve("blocked.txt")));
    }

    @Test
    void readOnlyModeAllowsReadTools(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ok.txt"), "data");
        ToolContext ctx = new ToolContext(dir, PermissionService.readOnly());
        String out = registry().dispatch("Read", "{\"file_path\":\"ok.txt\"}", ctx);
        assertTrue(out.contains("data"));
    }

    @Test
    void emitsSpecForEveryTool(@TempDir Path dir) {
        assertTrue(registry().specs().size() >= 3);
    }
}

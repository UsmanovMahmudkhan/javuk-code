package dev.javuk.mcp;

import dev.javuk.tools.ToolRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class McpToolTest {

    @Test
    void exposesNamespacedNameAndSchema() {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of("q", Map.of("type", "string")), List.of("q"), null, null, null);
        McpSchema.Tool def = new McpSchema.Tool("search", null, "Search the web",
                schema, null, null, null);

        McpTool tool = new McpTool(null, "web", def);

        assertEquals("web__search", tool.name());
        assertTrue(tool.description().contains("[MCP:web]"));
        assertTrue(tool.properties().containsKey("q"));
        assertEquals(List.of("q"), tool.required());
        assertTrue(tool.mutating());
    }

    /**
     * Live end-to-end check against the official filesystem MCP server over stdio.
     * Opt-in (downloads an npm package): run with {@code JAVUK_MCP_LIVE_TEST=1} and
     * {@code npx} available; otherwise skipped so the default suite stays hermetic.
     */
    @Test
    void connectsToRealStdioServer(@TempDir Path dir) {
        assumeTrue("1".equals(System.getenv("JAVUK_MCP_LIVE_TEST")), "live MCP test not enabled");
        assumeTrue(commandExists("npx"), "npx not available");

        McpManager manager = new McpManager();
        ToolRegistry registry = new ToolRegistry();
        McpServerConfig fs = new McpServerConfig("fs", "npx",
                List.of("-y", "@modelcontextprotocol/server-filesystem", dir.toString()),
                Map.of(), null);
        try {
            int registered = manager.connectAll(List.of(fs), registry, msg -> System.out.println(msg));
            assertTrue(registered > 0, "expected the filesystem server to advertise tools");
            assertTrue(registry.all().stream().anyMatch(t -> t.name().startsWith("fs__")),
                    "expected namespaced fs__ tools");
        } finally {
            manager.close();
        }
    }

    private static boolean commandExists(String cmd) {
        try {
            return new ProcessBuilder("sh", "-c", "command -v " + cmd)
                    .start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

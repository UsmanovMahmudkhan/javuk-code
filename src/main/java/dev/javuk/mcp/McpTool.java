package dev.javuk.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.tools.Tool;
import dev.javuk.tools.ToolContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts a tool discovered from an MCP server into a Javuk {@link Tool}. The
 * exposed name is namespaced as {@code <server>__<tool>} to avoid clashes with
 * built-ins; calls are forwarded to the server via the SDK's {@link McpSyncClient}.
 */
public final class McpTool implements Tool {

    private final McpSyncClient client;
    private final String serverName;
    private final McpSchema.Tool def;
    private final String exposedName;

    public McpTool(McpSyncClient client, String serverName, McpSchema.Tool def) {
        this.client = client;
        this.serverName = serverName;
        this.def = def;
        this.exposedName = serverName + "__" + def.name();
    }

    @Override
    public String name() {
        return exposedName;
    }

    @Override
    public String description() {
        String d = def.description();
        return "[MCP:" + serverName + "] " + (d == null || d.isBlank() ? def.name() : d);
    }

    @Override
    public Map<String, Object> properties() {
        McpSchema.JsonSchema schema = def.inputSchema();
        if (schema != null && schema.properties() != null) {
            return schema.properties();
        }
        return new LinkedHashMap<>();
    }

    @Override
    public List<String> required() {
        McpSchema.JsonSchema schema = def.inputSchema();
        if (schema != null && schema.required() != null) {
            return schema.required();
        }
        return List.of();
    }

    @Override
    public boolean mutating() {
        // MCP tools can have side effects; gate them through permissions to be safe.
        return true;
    }

    @Override
    public String preview(JsonNode args, ToolContext ctx) {
        return "call MCP tool " + exposedName + " " + args;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        Map<String, Object> arguments = args == null || args.isNull()
                ? Map.of()
                : dev.javuk.util.Json.MAPPER.convertValue(args, Map.class);

        McpSchema.CallToolResult result =
                client.callTool(new McpSchema.CallToolRequest(def.name(), arguments));

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content part : result.content()) {
            if (part instanceof McpSchema.TextContent text) {
                sb.append(text.text());
            }
        }
        if (Boolean.TRUE.equals(result.isError())) {
            return "Error from MCP tool: " + sb;
        }
        return sb.isEmpty() ? "(no content)" : sb.toString();
    }
}

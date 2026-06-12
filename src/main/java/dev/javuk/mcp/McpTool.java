package dev.javuk.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.tools.Tool;
import dev.javuk.tools.ToolContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts a tool discovered from an MCP server into a Javuk {@link Tool}. The
 * exposed name is namespaced as {@code <server>__<tool>} to avoid clashes with
 * built-ins; calls are forwarded to the server via {@link McpClient}.
 */
public final class McpTool implements Tool {

    private final McpClient client;
    private final String serverName;
    private final McpClient.ToolDef def;
    private final String exposedName;

    public McpTool(McpClient client, String serverName, McpClient.ToolDef def) {
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
    @SuppressWarnings("unchecked")
    public Map<String, Object> properties() {
        JsonNode schema = def.inputSchema();
        if (schema != null && schema.has("properties")) {
            return dev.javuk.util.Json.MAPPER.convertValue(schema.get("properties"), Map.class);
        }
        return new LinkedHashMap<>();
    }

    @Override
    public List<String> required() {
        JsonNode schema = def.inputSchema();
        List<String> req = new ArrayList<>();
        if (schema != null && schema.path("required").isArray()) {
            schema.get("required").forEach(n -> req.add(n.asText()));
        }
        return req;
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
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        return client.callTool(def.name(), args);
    }
}

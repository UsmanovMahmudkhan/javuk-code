package dev.javuk.mcp;

import dev.javuk.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Connects to configured MCP servers, registers their tools into the agent's
 * {@link ToolRegistry}, and owns the client lifecycle. Failures to connect are
 * reported but never fatal — Javuk runs fine with zero MCP servers.
 */
public final class McpManager implements AutoCloseable {

    private final List<McpClient> clients = new ArrayList<>();

    /** @return the number of tools registered across all servers. */
    public int connectAll(List<McpServerConfig> servers, ToolRegistry registry, Consumer<String> log) {
        int registered = 0;
        for (McpServerConfig server : servers) {
            try {
                McpClient client = McpClient.spawn(server);
                clients.add(client);
                List<McpClient.ToolDef> tools = client.initializeAndListTools();
                for (McpClient.ToolDef def : tools) {
                    registry.register(new McpTool(client, server.name(), def));
                    registered++;
                }
                log.accept("MCP: connected '" + server.name() + "' (" + tools.size() + " tools)");
            } catch (Exception e) {
                log.accept("MCP: failed to connect '" + server.name() + "': " + e.getMessage());
            }
        }
        return registered;
    }

    @Override
    public void close() {
        for (McpClient client : clients) {
            client.close();
        }
        clients.clear();
    }
}

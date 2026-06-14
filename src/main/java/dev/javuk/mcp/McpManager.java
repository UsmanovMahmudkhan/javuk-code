package dev.javuk.mcp;

import dev.javuk.tools.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Connects to configured MCP servers using the official MCP Java SDK, registers
 * their tools into the agent's {@link ToolRegistry}, and owns the client
 * lifecycle. Supports both <b>stdio</b> servers (spawned subprocess) and
 * <b>HTTP/SSE</b> servers (a URL). Failures to connect are reported but never
 * fatal — Javuk runs fine with zero MCP servers.
 */
public final class McpManager implements AutoCloseable {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final List<McpSyncClient> clients = new ArrayList<>();
    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();

    /** @return the number of tools registered across all servers. */
    public int connectAll(List<McpServerConfig> servers, ToolRegistry registry, Consumer<String> log) {
        int registered = 0;
        for (McpServerConfig server : servers) {
            try {
                McpSyncClient client = McpClient.sync(transportFor(server))
                        .requestTimeout(REQUEST_TIMEOUT)
                        .clientInfo(new McpSchema.Implementation("javuk", "1.0"))
                        .build();
                clients.add(client);
                client.initialize();

                List<McpSchema.Tool> tools = client.listTools().tools();
                for (McpSchema.Tool tool : tools) {
                    registry.register(new McpTool(client, server.name(), tool));
                    registered++;
                }
                log.accept("MCP: connected '" + server.name() + "' (" + tools.size() + " tools)");
            } catch (Exception e) {
                log.accept("MCP: failed to connect '" + server.name() + "': " + e.getMessage());
            }
        }
        return registered;
    }

    private McpClientTransport transportFor(McpServerConfig server) {
        if (server.isHttp()) {
            return HttpClientSseClientTransport.builder(server.url()).build();
        }
        ServerParameters params = ServerParameters.builder(server.command())
                .args(server.args())
                .env(server.env())
                .build();
        return new StdioClientTransport(params, jsonMapper);
    }

    @Override
    public void close() {
        for (McpSyncClient client : clients) {
            try {
                client.closeGracefully();
            } catch (Exception ignored) {
                client.close();
            }
        }
        clients.clear();
    }
}

package dev.javuk.mcp;

import java.util.List;
import java.util.Map;

/**
 * Configuration for one MCP server, declared under {@code "mcpServers"} in the
 * config file. The server is launched as a subprocess and spoken to over stdio.
 */
public record McpServerConfig(String name, String command, List<String> args, Map<String, String> env) {

    public McpServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}

package dev.javuk.mcp;

import java.util.List;
import java.util.Map;

/**
 * Configuration for one MCP server, declared under {@code "mcpServers"} in the
 * config file. Either a <b>stdio</b> server (a {@code command} launched as a
 * subprocess) or an <b>HTTP/SSE</b> server (a {@code url}).
 */
public record McpServerConfig(String name, String command, List<String> args,
                              Map<String, String> env, String url) {

    public McpServerConfig {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
    }

    /** Backwards-compatible stdio constructor. */
    public McpServerConfig(String name, String command, List<String> args, Map<String, String> env) {
        this(name, command, args, env, null);
    }

    /** True if this server is reached over HTTP/SSE rather than spawned over stdio. */
    public boolean isHttp() {
        return url != null && !url.isBlank();
    }
}

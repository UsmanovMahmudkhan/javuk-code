package dev.javuk.config;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.hooks.Hooks;
import dev.javuk.mcp.McpServerConfig;
import dev.javuk.util.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the effective {@link Config} with precedence
 * defaults &lt; config file &lt; environment. CLI flags are applied by the caller
 * on top (highest precedence).
 *
 * <p>Config files are JSON and read from {@code ~/.config/javuk/config.json}
 * (user) then {@code ./.javuk/config.json} (project, wins over user). Recognised
 * keys: {@code model}, {@code baseUrl}, {@code permissionMode}.
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static Config load() {
        Config config = new Config();

        // 1. Config files (user, then project overrides).
        applyFile(config, Path.of(System.getProperty("user.home"), ".config", "javuk", "config.json"));
        applyFile(config, Path.of(".javuk", "config.json"));

        // 2. Environment (overrides files).
        String apiKey = firstNonBlank(System.getenv("OPENROUTER_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (apiKey != null) {
            config.apiKey(apiKey);
        }
        config.baseUrl(System.getenv("OPENROUTER_BASE_URL"));
        config.model(System.getenv("JAVUK_MODEL"));
        config.permissionMode(System.getenv("JAVUK_PERMISSION_MODE"));

        return config;
    }

    private static void applyFile(Config config, Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonNode node = Json.MAPPER.readTree(Files.readString(path));
            config.model(text(node, "model"));
            config.baseUrl(text(node, "baseUrl"));
            config.permissionMode(text(node, "permissionMode"));
            JsonNode hooks = node.get("hooks");
            if (hooks != null) {
                config.hooks(new Hooks(stringList(hooks.get("preTool")),
                        stringList(hooks.get("postTool"))));
            }
            JsonNode mcp = node.get("mcpServers");
            if (mcp != null && mcp.isObject()) {
                config.mcpServers(parseMcpServers(mcp));
            }
        } catch (Exception ignored) {
            // malformed config file — fall back to defaults/env
        }
    }

    private static List<McpServerConfig> parseMcpServers(JsonNode mcp) {
        List<McpServerConfig> servers = new ArrayList<>();
        mcp.fields().forEachRemaining(entry -> {
            JsonNode s = entry.getValue();
            String command = s.path("command").asText(null);
            if (command == null || command.isBlank()) {
                return;
            }
            Map<String, String> env = new LinkedHashMap<>();
            JsonNode envNode = s.get("env");
            if (envNode != null && envNode.isObject()) {
                envNode.fields().forEachRemaining(e -> env.put(e.getKey(), e.getValue().asText()));
            }
            servers.add(new McpServerConfig(entry.getKey(), command, stringList(s.get("args")), env));
        });
        return servers;
    }

    private static List<String> stringList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

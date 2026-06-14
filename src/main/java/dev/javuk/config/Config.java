package dev.javuk.config;

import dev.javuk.hooks.Hooks;
import dev.javuk.mcp.McpServerConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Effective runtime configuration. Values are resolved with the precedence
 * defaults &lt; config file &lt; environment &lt; CLI flags (see
 * {@link ConfigLoader}). This object is the single source the agent reads from.
 */
public final class Config {

    public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
    public static final String DEFAULT_MODEL = "anthropic/claude-haiku-4.5";

    /** Default cap on output tokens per turn. Small enough to work on low-credit accounts. */
    public static final int DEFAULT_MAX_TOKENS = 4096;

    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private String model = DEFAULT_MODEL;
    private String provider = "openrouter";
    private String permissionMode = "ask";
    private String systemPrompt;
    private Path workingDir = Path.of("").toAbsolutePath();
    private Hooks hooks = Hooks.none();
    private List<McpServerConfig> mcpServers = List.of();
    private boolean allowPrivateFetch = false;
    private boolean allowOutsideWorkspace = false;
    private boolean sound = true;
    private int maxTokens = DEFAULT_MAX_TOKENS;

    public String apiKey() {
        return apiKey;
    }

    public Config apiKey(String v) {
        this.apiKey = v;
        return this;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Config baseUrl(String v) {
        if (v != null && !v.isBlank()) {
            this.baseUrl = v;
        }
        return this;
    }

    public String model() {
        return model;
    }

    public Config model(String v) {
        if (v != null && !v.isBlank()) {
            this.model = v;
        }
        return this;
    }

    public String provider() {
        return provider;
    }

    public Config provider(String v) {
        if (v != null && !v.isBlank()) {
            this.provider = v;
        }
        return this;
    }

    public String permissionMode() {
        return permissionMode;
    }

    public Config permissionMode(String v) {
        if (v != null && !v.isBlank()) {
            this.permissionMode = v;
        }
        return this;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public Config systemPrompt(String v) {
        this.systemPrompt = v;
        return this;
    }

    public Path workingDir() {
        return workingDir;
    }

    public Config workingDir(Path v) {
        if (v != null) {
            this.workingDir = v;
        }
        return this;
    }

    public Hooks hooks() {
        return hooks;
    }

    public Config hooks(Hooks v) {
        if (v != null) {
            this.hooks = v;
        }
        return this;
    }

    public List<McpServerConfig> mcpServers() {
        return mcpServers;
    }

    public Config mcpServers(List<McpServerConfig> v) {
        if (v != null) {
            this.mcpServers = List.copyOf(v);
        }
        return this;
    }

    /** When true, WebFetch may target private/internal/loopback hosts (default false). */
    public boolean allowPrivateFetch() {
        return allowPrivateFetch;
    }

    public Config allowPrivateFetch(boolean v) {
        this.allowPrivateFetch = v;
        return this;
    }

    /** When true, file/search tools may read or write outside the working dir (default false). */
    public boolean allowOutsideWorkspace() {
        return allowOutsideWorkspace;
    }

    public Config allowOutsideWorkspace(boolean v) {
        this.allowOutsideWorkspace = v;
        return this;
    }

    /** When true, the REPL plays notification sounds for turn/permission/error events. */
    public boolean sound() {
        return sound;
    }

    public Config sound(boolean v) {
        this.sound = v;
        return this;
    }

    /** Maximum output tokens the model may generate per turn. */
    public int maxTokens() {
        return maxTokens;
    }

    public Config maxTokens(int v) {
        if (v > 0) {
            this.maxTokens = v;
        }
        return this;
    }
}

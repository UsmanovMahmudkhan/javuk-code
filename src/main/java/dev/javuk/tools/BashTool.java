package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a shell command via {@code sh -c}, capturing combined stdout/stderr.
 * Output is capped and the process is killed if it exceeds the timeout so a
 * runaway command can't hang the agent.
 */
public final class BashTool implements Tool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 30_000;

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    public String description() {
        return "Execute a shell command and return its combined stdout/stderr. "
                + "Commands run in the working directory and are killed after a timeout.";
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of(
                "command", Map.of("type", "string", "description", "The shell command to execute"),
                "timeout", Map.of("type", "integer", "description",
                        "Timeout in seconds (optional, default " + DEFAULT_TIMEOUT_SECONDS + ")")
        );
    }

    @Override
    public List<String> required() {
        return List.of("command");
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String preview(JsonNode args, ToolContext ctx) {
        return "run: " + Json.str(args, "command", "?");
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) throws Exception {
        String command = Json.required(args, "command");
        int timeout = Json.intOr(args, "timeout", DEFAULT_TIMEOUT_SECONDS);

        Process process = new ProcessBuilder("sh", "-c", command)
                .directory(ctx.workingDir().toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes());

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return truncate(output) + "\n[command timed out after " + timeout + "s and was killed]";
        }

        int exit = process.exitValue();
        String body = truncate(output);
        if (exit != 0) {
            return body + (body.isEmpty() ? "" : "\n") + "[exit code " + exit + "]";
        }
        return body.isEmpty() ? "(command produced no output)" : body;
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_OUTPUT_CHARS) {
            return s;
        }
        return s.substring(0, MAX_OUTPUT_CHARS) + "\n… [output truncated, "
                + (s.length() - MAX_OUTPUT_CHARS) + " more chars]";
    }
}

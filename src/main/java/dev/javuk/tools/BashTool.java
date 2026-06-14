package dev.javuk.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.javuk.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a shell command via {@code sh -c}, capturing combined stdout/stderr.
 * Output is capped and the process is killed if it exceeds the timeout so a
 * runaway command can't hang the agent.
 *
 * <p>Output is drained on a separate virtual thread into a bounded buffer. This
 * is essential: reading the process stream on the calling thread would block
 * until the stream reaches EOF (i.e. the process exits), which would defeat the
 * timeout for commands that hang ({@code sleep 1000}) and read unbounded output
 * fully into memory ({@code cat /dev/zero}) before any cap could apply.
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

        // Drain output concurrently so a full pipe never blocks the child and so the
        // timeout below is enforced even when the command produces no output.
        BoundedDrain drain = new BoundedDrain(process.getInputStream(), MAX_OUTPUT_CHARS);
        Thread drainThread = Thread.ofVirtual().name("bash-drain").start(drain);

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();           // reap the killed process
            drainThread.join(1_000);     // let the drain finish at EOF (best-effort)
            String body = drain.text();
            return body + (body.isEmpty() ? "" : "\n")
                    + "[command timed out after " + timeout + "s and was killed]";
        }

        drainThread.join();              // process exited: stream is at EOF, drain returns
        int exit = process.exitValue();
        String body = drain.text();
        if (exit != 0) {
            return body + (body.isEmpty() ? "" : "\n") + "[exit code " + exit + "]";
        }
        return body.isEmpty() ? "(command produced no output)" : body;
    }

    /**
     * Reads a process stream to EOF, retaining at most {@code cap} bytes while
     * continuing to drain (and count) the rest so the child never blocks on a
     * full pipe. Memory use is bounded by {@code cap}.
     */
    private static final class BoundedDrain implements Runnable {
        private final InputStream in;
        private final int cap;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private volatile long dropped;

        BoundedDrain(InputStream in, int cap) {
            this.in = in;
            this.cap = cap;
        }

        @Override
        public void run() {
            byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    int room = cap - captured.size();
                    if (room > 0) {
                        int take = Math.min(room, n);
                        captured.write(buf, 0, take);
                        if (n > take) {
                            dropped += (n - take);
                        }
                    } else {
                        dropped += n;
                    }
                }
            } catch (IOException ignored) {
                // stream closed (e.g. the process was killed) — stop draining
            }
        }

        String text() {
            String s = captured.toString(StandardCharsets.UTF_8);
            if (dropped > 0) {
                return s + "\n… [output truncated, " + dropped + " more chars]";
            }
            return s;
        }
    }
}

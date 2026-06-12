package dev.javuk.hooks;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * User-defined shell commands run around tool execution, configured under
 * {@code "hooks"} in the config file. Each command runs with the tool name and
 * its JSON arguments exposed as environment variables ({@code JAVUK_TOOL},
 * {@code JAVUK_TOOL_ARGS}).
 *
 * <p>A <b>pre</b> hook that exits non-zero <em>blocks</em> the tool call (its
 * output becomes the reason). <b>post</b> hooks run after execution and are
 * best-effort.
 */
public final class Hooks {

    private static final int TIMEOUT_SECONDS = 30;

    private final List<String> preTool;
    private final List<String> postTool;

    public Hooks(List<String> preTool, List<String> postTool) {
        this.preTool = preTool == null ? List.of() : List.copyOf(preTool);
        this.postTool = postTool == null ? List.of() : List.copyOf(postTool);
    }

    public static Hooks none() {
        return new Hooks(List.of(), List.of());
    }

    public boolean isEmpty() {
        return preTool.isEmpty() && postTool.isEmpty();
    }

    /** @return a denial reason if a pre-hook blocked the call, else null. */
    public String runPre(String toolName, String argsJson, Path workingDir) {
        for (String command : preTool) {
            Result r = run(command, toolName, argsJson, workingDir);
            if (r.exitCode != 0) {
                return r.output.isBlank()
                        ? "pre-tool hook exited " + r.exitCode : r.output.strip();
            }
        }
        return null;
    }

    public void runPost(String toolName, String argsJson, Path workingDir) {
        for (String command : postTool) {
            run(command, toolName, argsJson, workingDir);
        }
    }

    private Result run(String command, String toolName, String argsJson, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                    .directory(workingDir.toFile())
                    .redirectErrorStream(true);
            pb.environment().put("JAVUK_TOOL", toolName);
            pb.environment().put("JAVUK_TOOL_ARGS", argsJson);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(124, "hook timed out");
            }
            return new Result(p.exitValue(), output);
        } catch (Exception e) {
            return new Result(1, "hook failed: " + e.getMessage());
        }
    }

    private record Result(int exitCode, String output) {
    }
}

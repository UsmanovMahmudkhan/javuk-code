package dev.javuk.agent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the system prompt for interactive sessions: the agent's identity and
 * tool-use guidance, the runtime environment, and any project context file
 * ({@code JAVUK.md}, {@code AGENTS.md}, or {@code CLAUDE.md}) found in the
 * working directory.
 */
public final class SystemPrompt {

    private static final String[] CONTEXT_FILES = {"JAVUK.md", "AGENTS.md", "CLAUDE.md"};

    private SystemPrompt() {
    }

    public static String build(Path workingDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are Javuk, a terminal coding agent. You help with software tasks by reading and \
                editing files and running shell commands through the tools provided.

                Guidelines:
                - Use the Read, Glob, and Grep tools to understand code before changing it.
                - Make focused edits with Edit/MultiEdit; use Write for new files.
                - Prefer running commands (build, tests) with Bash to verify your work.
                - Keep responses concise. Explain what you did, not what you are about to do.
                - When a task needs several steps, track them with TodoWrite.
                """);

        sb.append("\nEnvironment:\n");
        sb.append("- Working directory: ").append(workingDir).append('\n');
        sb.append("- OS: ").append(System.getProperty("os.name")).append('\n');

        String context = loadContext(workingDir);
        if (context != null) {
            sb.append("\nProject context (from context file):\n").append(context).append('\n');
        }
        return sb.toString();
    }

    private static String loadContext(Path workingDir) {
        for (String name : CONTEXT_FILES) {
            Path p = workingDir.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String body = Files.readString(p).strip();
                    if (!body.isEmpty()) {
                        return body.length() > 8000 ? body.substring(0, 8000) + "\n…[truncated]" : body;
                    }
                } catch (Exception ignored) {
                    // unreadable context file — skip
                }
            }
        }
        return null;
    }
}

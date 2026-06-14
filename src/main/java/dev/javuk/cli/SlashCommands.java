package dev.javuk.cli;

import dev.javuk.llm.ModelCatalog;
import dev.javuk.llm.Usage;
import dev.javuk.permission.PermissionMode;
import dev.javuk.tools.Tool;
import dev.javuk.ui.Ansi;

import java.io.PrintWriter;

/**
 * Parses and executes REPL slash commands (lines starting with {@code /}).
 * Anything not recognised is reported back to the user.
 */
public final class SlashCommands {

    public enum Action {HANDLED, EXIT}

    private SlashCommands() {
    }

    public static Action handle(String line, Repl repl) {
        PrintWriter out = repl.out();
        String[] parts = line.substring(1).trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].strip() : "";

        switch (cmd) {
            case "exit", "quit", "q" -> {
                return Action.EXIT;
            }
            case "help", "h", "?" -> printHelp(out);
            case "clear", "reset" -> {
                repl.conversation().reset();
                out.println(Ansi.gray("Conversation cleared."));
            }
            case "model" -> changeModel(repl, arg, out);
            case "models" -> {
                out.println(Ansi.bold("Models with known pricing:"));
                ModelCatalog.known().stream().sorted()
                        .forEach(m -> out.println("  " + m));
                out.println(Ansi.gray("(any model id your provider supports also works)"));
            }
            case "tools" -> {
                out.println(Ansi.bold("Available tools:"));
                for (Tool t : repl.tools().all()) {
                    out.println("  " + Ansi.cyan(t.name()) + Ansi.gray(" — " + t.description()));
                }
            }
            case "compact" -> {
                if (repl.compact()) {
                    out.println(Ansi.green("Conversation compacted into a summary."));
                }
            }
            case "cost" -> printCost(repl.usage(), repl.config().model(), out);
            case "tokens" -> {
                Usage u = repl.usage();
                out.printf("tokens — prompt: %d, completion: %d, total: %d (%d requests)%n",
                        u.promptTokens(), u.completionTokens(), u.totalTokens(), u.requests());
            }
            case "save" -> {
                String id = repl.saveSession(arg);
                out.println(id != null ? Ansi.green("Saved session as '" + id + "'")
                        : Ansi.red("Failed to save session."));
            }
            case "load" -> {
                if (arg.isBlank()) {
                    out.println(Ansi.red("Usage: /load <session-id>  (see /sessions)"));
                } else if (repl.loadSession(arg)) {
                    out.println(Ansi.green("Loaded session '" + arg + "'"));
                }
            }
            case "sessions" -> {
                var ids = repl.sessions().list();
                if (ids.isEmpty()) {
                    out.println(Ansi.gray("(no saved sessions)"));
                } else {
                    out.println(Ansi.bold("Saved sessions (newest first):"));
                    ids.forEach(id -> out.println("  " + id));
                }
            }
            case "permissions", "mode" -> changeMode(repl, arg, out);
            case "allow" -> {
                if (arg.isBlank()) {
                    out.println(Ansi.red("Usage: /allow <Tool>  or  /allow <Tool>: <prefix>"));
                    out.println(Ansi.gray("  e.g. /allow Read            (all Read actions)"));
                    out.println(Ansi.gray("       /allow Bash: git       (commands starting with git)"));
                } else {
                    repl.permissions().allowList().add(arg);
                    out.println(Ansi.green("Always allowing actions matching: " + arg));
                }
            }
            case "allowed" -> {
                var ps = repl.permissions().allowList().patterns();
                if (ps.isEmpty()) {
                    out.println(Ansi.gray("(no always-allow patterns)"));
                } else {
                    out.println(Ansi.bold("Always-allow patterns:"));
                    ps.forEach(p -> out.println("  " + p));
                }
            }
            case "system" -> {
                String sp = repl.config().systemPrompt();
                out.println(sp == null || sp.isBlank() ? Ansi.gray("(no system prompt set)") : sp);
            }
            case "commands" -> {
                var names = repl.customCommands() == null
                        ? java.util.Set.<String>of() : repl.customCommands().names();
                if (names.isEmpty()) {
                    out.println(Ansi.gray("(no custom commands; add .javuk/commands/<name>.md)"));
                } else {
                    out.println(Ansi.bold("Custom commands:"));
                    names.forEach(n -> out.println("  /" + n));
                }
            }
            case "agents", "agent" -> agents(repl, arg, out);
            default -> {
                if (!repl.tryCustomCommand(cmd, arg)) {
                    out.println(Ansi.red("Unknown command: /" + cmd) + Ansi.gray("  (try /help)"));
                }
            }
        }
        out.flush();
        return Action.HANDLED;
    }

    private static void agents(Repl repl, String arg, PrintWriter out) {
        var registry = repl.agents();
        if (arg.isBlank()) {
            if (registry == null || registry.isEmpty()) {
                out.println(Ansi.gray("(no agents; add .javuk/agents/<name>.md)"));
                return;
            }
            String active = repl.activeAgent();
            out.println(Ansi.bold("Agents:") + Ansi.gray("  (/agents <name> to switch, "
                    + "/agents default to reset)"));
            out.println("  " + (active == null ? Ansi.green("• default") : "  default")
                    + Ansi.gray(" — standard Javuk coding agent"));
            for (dev.javuk.agent.AgentDefinition a : registry.all()) {
                String marker = a.name().equals(active) ? Ansi.green("• ") : "  ";
                out.println("  " + marker + Ansi.cyan(a.name()) + Ansi.gray(" — " + a.description()));
            }
            return;
        }
        repl.applyAgent(arg);
    }

    private static void changeModel(Repl repl, String arg, PrintWriter out) {
        if (arg.isBlank()) {
            out.println("current model: " + Ansi.cyan(repl.config().model()));
            return;
        }
        repl.config().model(arg);
        repl.rebuildAgent();
        out.println(Ansi.green("Model set to " + arg));
    }

    private static void changeMode(Repl repl, String arg, PrintWriter out) {
        if (arg.isBlank()) {
            out.println("permission mode: " + Ansi.cyan(repl.permissions().mode().name().toLowerCase())
                    + Ansi.gray("  (ask | auto | plan)"));
            return;
        }
        PermissionMode mode = PermissionMode.from(arg);
        repl.permissions().setMode(mode);
        out.println(Ansi.green("Permission mode set to " + mode.name().toLowerCase()));
    }

    private static void printCost(Usage u, String model, PrintWriter out) {
        double cost = u.estimatedCostUsd(model);
        if (cost < 0) {
            out.println(Ansi.gray("cost — n/a (no pricing for " + model + "); total tokens: "
                    + u.totalTokens()));
        } else {
            out.printf("cost — ~$%.4f over %d requests (%d tokens)%n",
                    cost, u.requests(), u.totalTokens());
        }
    }

    private static void printHelp(PrintWriter out) {
        out.println(Ansi.bold("Javuk commands:"));
        String[][] rows = {
                {"/help", "show this help"},
                {"/clear", "clear the conversation (keep system prompt)"},
                {"/model [id]", "show or switch the model"},
                {"/models", "list models with known pricing"},
                {"/tools", "list available tools"},
                {"/permissions [mode]", "show or set permission mode (ask|auto|plan)"},
                {"/allow <Tool>[: prefix]", "always allow a tool, or actions whose preview starts with prefix"},
                {"/allowed", "list always-allow patterns"},
                {"/save [id]", "save the conversation to a session file"},
                {"/load <id>", "load a saved session"},
                {"/sessions", "list saved sessions"},
                {"/compact", "summarize the conversation to free context"},
                {"/commands", "list custom commands (.javuk/commands)"},
                {"/agents [name]", "list agents, or switch persona (default to reset)"},
                {"/cost", "show estimated session cost"},
                {"/tokens", "show token usage"},
                {"/system", "show the system prompt"},
                {"/exit", "quit Javuk"},
        };
        for (String[] r : rows) {
            out.printf("  %-22s %s%n", Ansi.cyan(r[0]), Ansi.gray(r[1]));
        }
    }
}

package dev.javuk.cli;

import dev.javuk.agent.Agent;
import dev.javuk.agent.AgentListener;
import dev.javuk.agent.Conversation;
import dev.javuk.agent.SystemPrompt;
import dev.javuk.config.Config;
import dev.javuk.llm.OpenAiCompatClient;
import dev.javuk.llm.Usage;
import dev.javuk.session.Session;
import dev.javuk.session.SessionStore;
import dev.javuk.permission.InteractivePermissionService;
import dev.javuk.permission.PermissionMode;
import dev.javuk.tools.ToolContext;
import dev.javuk.tools.ToolRegistry;
import dev.javuk.tools.Tools;
import dev.javuk.ui.Ansi;
import dev.javuk.ui.Banner;
import dev.javuk.ui.Spinner;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * The interactive read-eval-print loop. Reads prompts with JLine (history,
 * editing), routes slash commands, and otherwise drives the {@link Agent},
 * streaming output and tool activity live.
 */
public final class Repl {

    private final Config config;
    private final Usage usage = new Usage();
    private final ToolRegistry tools;
    private final SessionStore sessions = new SessionStore();

    private Conversation conversation;
    private CustomCommands customCommands;
    private dev.javuk.agent.AgentRegistry agents;
    private String activeAgent; // null = default Javuk persona
    private final dev.javuk.mcp.McpManager mcp = new dev.javuk.mcp.McpManager();
    private String resumeId;
    private Terminal terminal;
    private LineReader reader;
    private PrintWriter out;
    private InteractivePermissionService permissions;
    private dev.javuk.llm.LlmClient llm;
    private Agent agent;

    public Repl(Config config) {
        this.config = config;
        this.tools = Tools.defaultRegistry(config.allowPrivateFetch());
    }

    /** Resume a saved session at startup. Pass "" to continue the most recent one. */
    public Repl resume(String sessionId) {
        this.resumeId = sessionId;
        return this;
    }

    public int run() {
        try {
            terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            System.err.println("Failed to start terminal: " + e.getMessage());
            return 1;
        }
        out = terminal.writer();
        reader = LineReaderBuilder.builder().terminal(terminal).build();

        permissions = new InteractivePermissionService(
                PermissionMode.from(config.permissionMode()),
                question -> {
                    try {
                        return reader.readLine(Ansi.yellow(question));
                    } catch (UserInterruptException | EndOfFileException e) {
                        return "n";
                    }
                });

        String systemPrompt = SystemPrompt.build(config.workingDir());
        config.systemPrompt(systemPrompt);
        customCommands = new CustomCommands(config.workingDir());
        agents = new dev.javuk.agent.AgentRegistry(config.workingDir());
        initConversation(systemPrompt);

        out.println(Banner.render(config.model(), permissions.mode().name().toLowerCase()));
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            out.println(Ansi.yellow("⚠ No API key set (OPENROUTER_API_KEY). "
                    + "Slash commands work; sending a prompt will fail until a key is set."));
        }
        if (!config.mcpServers().isEmpty()) {
            mcp.connectAll(config.mcpServers(), tools, msg -> out.println(Ansi.gray(msg)));
        }
        out.flush();

        while (true) {
            String line;
            try {
                line = reader.readLine(Ansi.cyan("javuk> "));
            } catch (UserInterruptException e) {   // Ctrl-C: clear current line, keep going
                continue;
            } catch (EndOfFileException e) {        // Ctrl-D: quit
                break;
            }
            if (line == null) {
                break;
            }
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("/")) {
                SlashCommands.Action action = SlashCommands.handle(line, this);
                if (action == SlashCommands.Action.EXIT) {
                    break;
                }
                continue;
            }

            runTurn(Mentions.expand(line, config.workingDir()));
        }

        mcp.close();
        out.println(Ansi.gray("Goodbye."));
        out.flush();
        return 0;
    }

    private void runTurn(String prompt) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            out.println(Ansi.red("No API key set. Export OPENROUTER_API_KEY and restart."));
            out.flush();
            return;
        }
        if (agent == null) {
            rebuildAgent();
        }
        conversation.addUser(prompt);

        // Run on a worker thread so Ctrl-C cancels the turn instead of killing Javuk.
        Thread worker = new Thread(() -> {
            try {
                agent.run(conversation, new ReplListener());
            } catch (Exception e) {
                dev.javuk.util.Logging.error("agent turn failed", e);
                out.println(Ansi.red("Error: " + e.getMessage()));
            }
        }, "javuk-turn");

        Terminal.SignalHandler previous = terminal.handle(Terminal.Signal.INT, sig -> worker.interrupt());
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
        } finally {
            terminal.handle(Terminal.Signal.INT, previous);
        }
        out.println();
        out.flush();
    }

    /** If {@code name} is a user-defined command, expand and run it. Returns true if handled. */
    boolean tryCustomCommand(String name, String args) {
        if (customCommands == null || !customCommands.has(name)) {
            return false;
        }
        String prompt = customCommands.expand(name, args);
        if (prompt == null) {
            return false;
        }
        runTurn(Mentions.expand(prompt, config.workingDir()));
        return true;
    }

    CustomCommands customCommands() {
        return customCommands;
    }

    private void initConversation(String systemPrompt) {
        if (resumeId != null) {
            String id = resumeId.isBlank() ? sessions.mostRecent() : resumeId;
            if (id != null) {
                try {
                    Session s = sessions.load(id);
                    config.model(s.model());
                    conversation = Conversation.fromEntries(s.entries(), systemPrompt);
                    out.println(Ansi.green("Resumed session '" + id + "' ("
                            + s.entries().size() + " messages)."));
                    return;
                } catch (Exception e) {
                    out.println(Ansi.red("Could not resume session: " + e.getMessage()));
                }
            } else {
                out.println(Ansi.yellow("No saved sessions to resume."));
            }
        }
        conversation = new Conversation().withSystemPrompt(systemPrompt);
    }

    /** Saves the current conversation under the given id (or a timestamp id). */
    String saveSession(String name) {
        String id = (name == null || name.isBlank())
                ? "session-" + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                : name;
        try {
            sessions.save(Session.of(id, config.model(),
                    java.time.Instant.now().toString(), conversation));
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    /** Loads a saved session into the current REPL, replacing history. */
    boolean loadSession(String name) {
        try {
            Session s = sessions.load(name);
            config.model(s.model());
            conversation = Conversation.fromEntries(s.entries(), config.systemPrompt());
            rebuildAgent();
            return true;
        } catch (Exception e) {
            out.println(Ansi.red("Could not load session: " + e.getMessage()));
            return false;
        }
    }

    SessionStore sessions() {
        return sessions;
    }

    /** Summarizes the conversation via the LLM and replaces history with the summary. */
    boolean compact() {
        if (llm == null) {
            rebuildAgent();
        }
        if (llm == null) {
            out.println(Ansi.red("No API key set; cannot compact."));
            return false;
        }
        StringBuilder transcript = new StringBuilder();
        for (Conversation.Entry e : conversation.entries()) {
            if (e.content() != null && !e.content().isBlank()) {
                transcript.append(e.role()).append(": ").append(e.content()).append("\n");
            }
        }
        Conversation summarizer = new Conversation();
        summarizer.addUser("Summarize this conversation concisely, preserving decisions, "
                + "file paths, and any unfinished tasks:\n\n" + transcript);
        try {
            String summary = llm.chat(summarizer.systemPrompt(), summarizer.chatMessages(),
                    java.util.List.of(), s -> {}).content();
            conversation.reset();
            conversation.addUser("[Summary of earlier conversation]\n" + summary);
            return true;
        } catch (Exception e) {
            out.println(Ansi.red("Compact failed: " + e.getMessage()));
            return false;
        }
    }

    /** (Re)builds the agent for the current model. No-op without an API key. */
    void rebuildAgent() {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            this.agent = null;
            return;
        }
        this.llm = dev.javuk.llm.LlmClients.create(config, usage);
        ToolContext ctx = new ToolContext(config.workingDir(), permissions, config.hooks(),
                config.allowOutsideWorkspace());
        java.util.List<String> agentTypes = agents == null
                ? java.util.List.of() : new java.util.ArrayList<>(agents.names());
        tools.register(new dev.javuk.tools.TaskTool(
                (type, desc, p) -> dev.javuk.agent.SubAgent.run(llm, config, usage, ctx, agents, type, p),
                agentTypes));

        // The master registry keeps every tool (incl. MCP + Task). When a restricting
        // persona is active, the agent sees only that persona's subset.
        ToolRegistry effective = tools;
        if (activeAgent != null && agents != null) {
            dev.javuk.agent.AgentDefinition def = agents.get(activeAgent);
            if (def != null && def.restrictsTools()) {
                effective = tools.subset(def.tools());
            }
        }
        this.agent = new Agent(llm, effective, ctx);
    }

    /**
     * Switches the main session to a named agent persona (system prompt + restricted
     * tools + optional model), or restores the default Javuk agent when {@code name}
     * is null/"default"/"reset". Resets the conversation, since the system prompt
     * changes. Prints the outcome.
     */
    void applyAgent(String name) {
        if (name == null || name.isBlank()
                || name.equalsIgnoreCase("default") || name.equalsIgnoreCase("reset")) {
            activeAgent = null;
            String sp = SystemPrompt.build(config.workingDir());
            config.systemPrompt(sp);
            conversation = new Conversation().withSystemPrompt(sp);
            rebuildAgent();
            out.println(Ansi.green("Switched to the default Javuk agent. Conversation reset."));
            return;
        }
        dev.javuk.agent.AgentDefinition def = agents == null ? null : agents.get(name);
        if (def == null) {
            out.println(Ansi.red("Unknown agent: " + name) + Ansi.gray("  (try /agents)"));
            return;
        }
        activeAgent = def.name();
        config.systemPrompt(def.systemPrompt());
        if (def.model() != null) {
            config.model(def.model());
        }
        conversation = new Conversation().withSystemPrompt(def.systemPrompt());
        rebuildAgent();
        String tools = def.restrictsTools()
                ? "  (tools: " + String.join(", ", def.tools()) + ")" : "";
        out.println(Ansi.green("Switched to agent '" + def.name() + "'. Conversation reset.")
                + Ansi.gray(tools));
    }

    dev.javuk.agent.AgentRegistry agents() {
        return agents;
    }

    String activeAgent() {
        return activeAgent;
    }

    // --- accessors used by SlashCommands ---
    Config config() {
        return config;
    }

    Usage usage() {
        return usage;
    }

    ToolRegistry tools() {
        return tools;
    }

    Conversation conversation() {
        return conversation;
    }

    InteractivePermissionService permissions() {
        return permissions;
    }

    PrintWriter out() {
        return out;
    }

    /** Renders the agent's progress live: spinner, streamed text, tool activity. */
    private final class ReplListener implements AgentListener {
        private Spinner spinner;
        private boolean streaming;

        @Override
        public void onThinking() {
            spinner = new Spinner(System.out, "thinking…");
            spinner.start();
        }

        @Override
        public void onAssistantDelta(String fragment) {
            stopSpinner();
            if (!streaming) {
                out.print(Ansi.green("● "));
                streaming = true;
            }
            out.print(fragment);
            out.flush();
        }

        @Override
        public void onToolCall(String name, String arguments) {
            stopSpinner();
            finishStreamingLine();
            out.println(Ansi.magenta("⚙ " + name) + " " + Ansi.gray(oneLine(arguments)));
            out.flush();
        }

        @Override
        public void onToolResult(String name, String result) {
            out.println(Ansi.gray("  ↳ " + oneLine(result)));
            out.flush();
        }

        @Override
        public void onAssistantText(String text) {
            finishStreamingLine();
        }

        private void stopSpinner() {
            if (spinner != null) {
                spinner.stop();
                spinner = null;
            }
        }

        private void finishStreamingLine() {
            if (streaming) {
                out.println();
                streaming = false;
            }
        }

        private String oneLine(String s) {
            String first = s.replace("\n", " ").strip();
            return first.length() > 100 ? first.substring(0, 100) + "…" : first;
        }
    }
}

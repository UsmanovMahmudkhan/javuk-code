package dev.javuk.cli;

import dev.javuk.agent.Agent;
import dev.javuk.agent.AgentListener;
import dev.javuk.agent.Conversation;
import dev.javuk.agent.SubAgent;
import dev.javuk.config.Config;
import dev.javuk.config.ConfigLoader;
import dev.javuk.llm.Provider;
import dev.javuk.llm.Usage;
import dev.javuk.permission.PermissionService;
import dev.javuk.tools.TaskTool;
import dev.javuk.tools.ToolContext;
import dev.javuk.tools.ToolRegistry;
import dev.javuk.tools.Tools;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command-line front door for Javuk. With {@code -p <prompt>} it runs a single
 * non-interactive turn (the CodeCrafters contract: final answer to stdout);
 * with no prompt it launches the interactive REPL. Flags override config/env.
 */
@Command(name = "javuk", mixinStandardHelpOptions = true, version = "Javuk 1.0",
        description = "A coding agent in Java — interactive REPL or one-shot (-p).")
public final class JavukCli implements Callable<Integer> {

    @Option(names = "-p", description = "Run a single prompt non-interactively and print the answer.")
    String prompt;

    @Option(names = "--model", description = "Model id (e.g. anthropic/claude-haiku-4.5).")
    String model;

    @Option(names = "--base-url", description = "OpenAI-compatible base URL (overrides --provider).")
    String baseUrl;

    @Option(names = "--provider",
            description = "Provider preset: openrouter (default), openai, ollama, anthropic (native).")
    String provider;

    @Option(names = "--yolo", description = "Auto-approve all tool actions (no permission prompts).")
    boolean yolo;

    @Option(names = {"--readonly", "--plan"}, description = "Block all mutating tools (plan mode).")
    boolean readonly;

    @Option(names = "--allow-private-fetch",
            description = "Allow WebFetch to reach private/internal hosts (off by default).")
    boolean allowPrivateFetch;

    @Option(names = "--allow-outside-workspace",
            description = "Allow file/search tools to read/write outside the working dir (off by default).")
    boolean allowOutsideWorkspace;

    @Option(names = "--resume", arity = "0..1", fallbackValue = "",
            description = "Resume a saved session by id, or the most recent if no id is given.")
    String resume;

    /** @return process exit code. */
    public int run(String[] args) {
        return new CommandLine(this).execute(args);
    }

    @Override
    public Integer call() {
        Config config = ConfigLoader.load();

        // Provider preset fills baseUrl + a default key; --base-url still wins.
        if (provider != null) {
            Provider p = Provider.from(provider);
            config.provider(p.name().toLowerCase());
            config.baseUrl(p.baseUrl());
            String key = System.getenv(p.apiKeyEnv());
            config.apiKey(key != null ? key : p.defaultApiKey() != null ? p.defaultApiKey() : config.apiKey());
        }
        config.model(model).baseUrl(baseUrl);

        if (yolo) {
            config.permissionMode("auto");
        } else if (readonly) {
            config.permissionMode("plan");
        }
        if (allowPrivateFetch) {
            config.allowPrivateFetch(true);
        }
        if (allowOutsideWorkspace) {
            config.allowOutsideWorkspace(true);
        }

        if (prompt != null) {
            return runOneShot(config);
        }
        Repl repl = new Repl(config);
        if (resume != null) {
            repl.resume(resume);
        }
        return repl.run();
    }

    private int runOneShot(Config config) {
        if (config.apiKey() == null || config.apiKey().isEmpty()) {
            System.err.println("Error: set OPENROUTER_API_KEY (or OPENAI_API_KEY).");
            return 1;
        }

        Usage usage = new Usage();
        var llm = dev.javuk.llm.LlmClients.create(config, usage);
        ToolRegistry tools = Tools.defaultRegistry(config.allowPrivateFetch());
        // One-shot is non-interactive: cannot prompt, so allow tool actions.
        ToolContext ctx = new ToolContext(config.workingDir(), PermissionService.allowAll(),
                config.hooks(), config.allowOutsideWorkspace());
        tools.register(new TaskTool((desc, p) -> SubAgent.run(llm, ctx, p)));
        Agent agent = new Agent(llm, tools, ctx);

        Conversation conversation = new Conversation();
        conversation.addUser(prompt);

        String answer = agent.run(conversation, AgentListener.noop());
        System.out.print(answer);
        return 0;
    }
}

package dev.javuk.agent;

import dev.javuk.config.Config;
import dev.javuk.llm.LlmClient;
import dev.javuk.llm.LlmClients;
import dev.javuk.llm.Usage;
import dev.javuk.tools.ToolContext;
import dev.javuk.tools.ToolRegistry;
import dev.javuk.tools.Tools;

/**
 * Runs a delegated task in a fresh, isolated agent context. The sub-agent shares
 * the parent's LLM client and tool context (working directory + permissions) but
 * gets a clean conversation and its own focused system prompt. It has the full
 * built-in tool set <em>except</em> the Task tool, which prevents unbounded
 * recursive delegation.
 *
 * <p>When a {@code subagent_type} names an {@link AgentDefinition} in the
 * {@link AgentRegistry}, that persona's system prompt, restricted tool set, and
 * optional model override are applied instead of the generic defaults.
 */
public final class SubAgent {

    private static final String GENERIC_PROMPT = """
            You are a focused sub-agent invoked to complete one delegated task.
            Use your tools to do the work, then report back a concise summary of
            what you found or changed. Do not ask questions — make reasonable
            decisions and finish the task.
            """;

    private SubAgent() {
    }

    /**
     * Runs a delegated task, optionally as a named agent persona.
     *
     * @param parentLlm the session's LLM client (used unless the persona overrides the model)
     * @param config    the session config (read for tool defaults and the model override client)
     * @param usage     token accounting, shared with the session
     * @param ctx       working dir + permissions, shared with the session
     * @param agents    available personas, or null for none
     * @param type      selected persona name, or null/unknown for the generic sub-agent
     * @param task      the full instructions for the sub-agent
     */
    public static String run(LlmClient parentLlm, Config config, Usage usage, ToolContext ctx,
                             AgentRegistry agents, String type, String task) {
        AgentDefinition def = agents == null ? null : agents.get(type);

        boolean allowPrivateFetch = config != null && config.allowPrivateFetch();
        ToolRegistry tools = Tools.defaultRegistry(allowPrivateFetch); // base tools only — no Task
        if (def != null && def.restrictsTools()) {
            tools = tools.subset(def.tools());
        }

        String systemPrompt = def != null && !def.systemPrompt().isBlank()
                ? def.systemPrompt() : GENERIC_PROMPT;

        LlmClient llm = parentLlm;
        if (def != null && def.model() != null && config != null && usage != null) {
            try {
                Config override = new Config()
                        .provider(config.provider())
                        .apiKey(config.apiKey())
                        .baseUrl(config.baseUrl())
                        .model(def.model())
                        .maxTokens(config.maxTokens());
                llm = LlmClients.create(override, usage);
            } catch (Exception ignored) {
                llm = parentLlm; // model override is best-effort
            }
        }

        Agent agent = new Agent(llm, tools, ctx);
        Conversation conversation = new Conversation().withSystemPrompt(systemPrompt);
        conversation.addUser(task);
        return agent.run(conversation, AgentListener.noop());
    }
}

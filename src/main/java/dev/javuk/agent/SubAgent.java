package dev.javuk.agent;

import dev.javuk.llm.LlmClient;
import dev.javuk.tools.ToolContext;
import dev.javuk.tools.ToolRegistry;
import dev.javuk.tools.Tools;

/**
 * Runs a delegated task in a fresh, isolated agent context. The sub-agent shares
 * the parent's LLM client and tool context (working directory + permissions) but
 * gets a clean conversation and its own focused system prompt. It has the full
 * built-in tool set <em>except</em> the Task tool, which prevents unbounded
 * recursive delegation.
 */
public final class SubAgent {

    private static final String SYSTEM_PROMPT = """
            You are a focused sub-agent invoked to complete one delegated task.
            Use your tools to do the work, then report back a concise summary of
            what you found or changed. Do not ask questions — make reasonable
            decisions and finish the task.
            """;

    private SubAgent() {
    }

    public static String run(LlmClient llm, ToolContext ctx, String task) {
        ToolRegistry tools = Tools.defaultRegistry(); // base tools only — no Task
        Agent agent = new Agent(llm, tools, ctx);
        Conversation conversation = new Conversation().withSystemPrompt(SYSTEM_PROMPT);
        conversation.addUser(task);
        return agent.run(conversation, AgentListener.noop());
    }
}

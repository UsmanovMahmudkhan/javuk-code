package dev.javuk.agent;

import dev.javuk.llm.AssistantTurn;
import dev.javuk.llm.LlmClient;
import dev.javuk.tools.Tool;
import dev.javuk.tools.ToolContext;
import dev.javuk.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The agentic loop: ask the model, run any tools it requests, feed the results
 * back, and repeat until the model produces a final answer with no tool calls.
 * Each step is reported through an {@link AgentListener} so callers can render it.
 */
public final class Agent {

    /** Safety cap so a misbehaving model can't loop forever. */
    private static final int MAX_STEPS = 50;

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final ToolContext toolContext;

    public Agent(LlmClient llm, ToolRegistry tools, ToolContext toolContext) {
        this.llm = llm;
        this.tools = tools;
        this.toolContext = toolContext;
    }

    /**
     * Runs the loop on the given conversation until the model stops calling
     * tools, then returns its final text answer.
     */
    public String run(Conversation conversation, AgentListener listener) {
        for (int step = 0; step < MAX_STEPS; step++) {
            if (Thread.currentThread().isInterrupted()) {
                String msg = "[cancelled]";
                listener.onAssistantText(msg);
                return msg;
            }
            listener.onThinking();
            AssistantTurn turn = llm.chat(conversation.systemPrompt(),
                    conversation.chatMessages(), tools.all(), listener::onAssistantDelta);

            conversation.addAssistantTurn(turn);

            if (!turn.hasToolCalls()) {
                listener.onAssistantText(turn.content());
                return turn.content();
            }

            List<AssistantTurn.ToolCall> calls = turn.toolCalls();
            List<String> results = (calls.size() > 1 && calls.stream().allMatch(this::isReadOnly))
                    ? runParallel(calls, listener)
                    : runSerial(calls, listener);

            for (int i = 0; i < calls.size(); i++) {
                conversation.addToolResult(calls.get(i).id(), results.get(i));
            }
        }
        String msg = "[stopped: reached the maximum of " + MAX_STEPS + " steps]";
        listener.onAssistantText(msg);
        return msg;
    }

    private List<String> runSerial(List<AssistantTurn.ToolCall> calls, AgentListener listener) {
        List<String> results = new ArrayList<>();
        for (AssistantTurn.ToolCall call : calls) {
            listener.onToolCall(call.name(), call.arguments());
            String result = tools.dispatch(call.name(), call.arguments(), toolContext);
            listener.onToolResult(call.name(), result);
            results.add(result);
        }
        return results;
    }

    /** Runs independent read-only tool calls concurrently, preserving order. */
    private List<String> runParallel(List<AssistantTurn.ToolCall> calls, AgentListener listener) {
        for (AssistantTurn.ToolCall call : calls) {
            listener.onToolCall(call.name(), call.arguments());
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>();
            for (AssistantTurn.ToolCall call : calls) {
                futures.add(pool.submit(() ->
                        tools.dispatch(call.name(), call.arguments(), toolContext)));
            }
            List<String> results = new ArrayList<>();
            for (int i = 0; i < calls.size(); i++) {
                String result;
                try {
                    result = futures.get(i).get();
                } catch (Exception e) {
                    result = "Error executing " + calls.get(i).name() + ": " + e.getMessage();
                }
                listener.onToolResult(calls.get(i).name(), result);
                results.add(result);
            }
            return results;
        }
    }

    private boolean isReadOnly(AssistantTurn.ToolCall call) {
        Tool tool = tools.get(call.name());
        return tool != null && !tool.mutating();
    }
}

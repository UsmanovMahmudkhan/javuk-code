---
title: "Building a Coding Agent in Java, Part 1: The Agent Loop"
published: false
description: "How an LLM-powered coding agent actually works — the loop, tool calling, and why it's simpler than it looks. Built in Java from scratch."
tags: java, ai, llm, tutorial
series: "Building a coding agent in Java"
canonical_url:
cover_image:
---

> This is part 1 of a series where I turn a 200-line CodeCrafters exercise into
> **Javuk**, a real terminal coding agent in Java. Code:
> https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

Coding agents like Claude Code feel magical, but the core is a loop you can fit
on a napkin. In this post we build that core in Java.

## The whole idea in one diagram

```
prompt ──▶ ask the model ──▶ does it want a tool?
                 ▲                   │ yes            │ no
                 │                   ▼                ▼
            tool results ◀── run the tool         final answer
```

The model doesn't run code. It *asks* to run code — "please call `Read` with
`{file_path: "Main.java"}"` — and we run it and hand back the result. Repeat
until the model stops asking for tools.

## Talking to the model

Javuk uses the OpenAI Java SDK, which works against any OpenAI-compatible
endpoint (OpenRouter, OpenAI, a local Ollama). A tool is just a function
description with a JSON schema:

```java
ChatCompletionTool readTool = ChatCompletionTool.builder()
    .function(FunctionDefinition.builder()
        .name("Read")
        .description("Read and return the contents of a file")
        .parameters(/* JSON schema: { file_path: string } */)
        .build())
    .build();
```

We send the conversation plus the list of tools. The model replies with either
text or one or more **tool calls**.

## The loop

```java
public String run(Conversation conversation, AgentListener listener) {
    for (int step = 0; step < MAX_STEPS; step++) {
        AssistantTurn turn = llm.chat(
            conversation.messages(), tools.specs(), listener::onAssistantDelta);
        conversation.addAssistantTurn(turn);

        if (!turn.hasToolCalls()) {
            return turn.content();          // model is done
        }

        for (AssistantTurn.ToolCall call : turn.toolCalls()) {
            String result = tools.dispatch(call.name(), call.arguments(), toolContext);
            conversation.addToolResult(call.id(), result);   // feed it back
        }
    }
}
```

That's the entire agent. Everything else — streaming, permissions, sessions — is
quality-of-life around this loop.

## Two details that matter

**`MAX_STEPS`.** A model can get stuck calling tools forever. A hard cap turns a
runaway bill into a graceful "I gave up after N steps."

**Tool-call ids.** Each tool result must reference the `id` of the call it
answers, so the model can match results to requests when it asked for several at
once.

## A provider-neutral result

Notice `run()` never touches the SDK's response types — it works with a tiny
record:

```java
public record AssistantTurn(String content, List<ToolCall> toolCalls) {
    public record ToolCall(String id, String name, String arguments) {}
}
```

This one decision pays off later: streaming and non-streaming clients return the
same shape, and adding a new provider is one class.

## Next up

In [part 2](02-tools-and-permissions.md) we build the tool suite — Read, Write,
Edit, Bash, Glob, Grep — behind a clean `Tool` interface, and add a permission
system so the agent can't `rm -rf` your repo without asking.

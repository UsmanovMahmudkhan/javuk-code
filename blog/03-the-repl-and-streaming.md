---
title: "Building a Coding Agent in Java, Part 3: The REPL and Streaming"
published: false
description: "Token-by-token streaming, accumulating tool calls out of a stream, and a JLine REPL with history and slash commands. The part that makes it feel alive."
tags: java, ai, llm, tutorial
series: "Building a coding agent in Java"
canonical_url:
cover_image:
---

> Final part of building **Javuk**, a terminal coding agent in Java.
> [Part 1](01-the-agent-loop.md) · [Part 2](02-tools-and-permissions.md). Code:
> https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

We have a loop and tools. Now the difference between a demo and something you'd
actually use: it has to feel *alive*.

## Streaming — and the tricky part

Streaming text is easy: print each delta as it arrives. The tricky part is that
**tool calls also stream** — in fragments, across many chunks, keyed by index:

```
chunk 1: toolCalls[0].id = "call_abc", function.name = "Ed"
chunk 2: toolCalls[0].function.arguments = "{\"file_pa"
chunk 3: toolCalls[0].function.arguments = "th\":\"x\"}"
```

So we accumulate by index until the stream ends:

```java
TreeMap<Long, PartialToolCall> partials = new TreeMap<>();

for (ChatCompletionChunk chunk : stream) {
    var delta = chunk.choices().get(0).delta();

    delta.content().ifPresent(text -> { content.append(text); onContentDelta.accept(text); });

    delta.toolCalls().ifPresent(calls -> {
        for (var tc : calls) {
            PartialToolCall p = partials.computeIfAbsent(tc.index(), k -> new PartialToolCall());
            tc.id().ifPresent(id -> p.id = id);
            tc.function().ifPresent(fn -> {
                fn.name().ifPresent(n -> p.name = n);
                fn.arguments().ifPresent(a -> p.arguments.append(a));   // concatenate!
            });
        }
    });
}
```

Text streams straight to the screen; tool-call arguments get *concatenated* into
valid JSON by the time the stream completes, then assembled into the same
`AssistantTurn` the non-streaming path returns. The agent loop doesn't even know
streaming happened.

## A REPL with JLine

`System.console().readLine()` gives you nothing — no history, no editing. JLine
gives you a real line editor in a few lines:

```java
Terminal terminal = TerminalBuilder.builder().system(true).build();
LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

while (true) {
    String line;
    try {
        line = reader.readLine("javuk> ");
    } catch (UserInterruptException e) { continue; }   // Ctrl-C: cancel line
    catch (EndOfFileException e)      { break; }        // Ctrl-D: quit
    // … handle slash command or run a turn
}
```

Arrow-key history, in-line editing, and sane Ctrl-C/Ctrl-D — for free.

## Slash commands

Anything starting with `/` is a command, not a prompt: `/model`, `/tools`,
`/cost`, `/save`, `/clear`. It's just a `switch` over the first word — cheap, and
it makes the tool feel finished.

## Rendering the agent's thoughts

A small `AgentListener` turns loop events into a live view: a spinner while the
model thinks, green text as it streams, a `⚙ ToolName` line when it calls a tool,
and a dim `↳ result` summary after. The loop fires the events; the REPL decides
how to draw them. The same loop runs silently in one-shot (`-p`) mode.

## What it adds up to

Start with a napkin loop. Add a `Tool` interface, a registry that doubles as the
model's API, a one-method permission gate, streaming with tool-call assembly, and
a JLine REPL — and you have a coding agent that's genuinely pleasant to use, in
about a dozen small Java classes. Every piece is testable, and the seams
(`LlmClient`, `Tool`, `PermissionService`) mean each can change without touching
the others.

Clone it, read it, extend it:
https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

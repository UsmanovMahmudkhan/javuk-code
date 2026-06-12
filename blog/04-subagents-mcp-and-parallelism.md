---
title: "Building a Coding Agent in Java, Part 4: Subagents, MCP, and Going Parallel"
published: false
description: "Three features that take a toy agent to a real one: delegating to subagents, loading tools from MCP servers, and running tool calls in parallel — in Java."
tags: java, ai, llm, mcp
series: "Building a coding agent in Java"
canonical_url:
cover_image:
---

> Part 4 of building **Javuk**, a terminal coding agent in Java.
> [1](01-the-agent-loop.md) · [2](02-tools-and-permissions.md) · [3](03-the-repl-and-streaming.md).
> Code: https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

The [first three parts](01-the-agent-loop.md) built a working agent. Here are
the three upgrades that make it feel *capable*.

## 1. Subagents — delegation for free

Because the agent loop is just a class, a tool can *start another one*. The
`Task` tool delegates a self-contained subtask to a nested agent:

```java
public static String run(LlmClient llm, ToolContext ctx, String task) {
    ToolRegistry tools = Tools.defaultRegistry();   // base tools, but NO Task tool
    Agent agent = new Agent(llm, tools, ctx);
    Conversation conv = new Conversation().withSystemPrompt(SUBAGENT_PROMPT);
    conv.addUser(task);
    return agent.run(conv, AgentListener.noop());
}
```

The sub-agent shares the working directory and permission gate but gets a clean
conversation — so a big job ("audit these 5 files") becomes several focused ones
without polluting the main context. Leaving `Task` out of the sub-agent's
registry is the one rule that prevents infinite recursion.

## 2. MCP — tools from anywhere

The [Model Context Protocol](https://modelcontextprotocol.io) is how agents load
*external* tools (filesystems, GitHub, databases, …). The stdio transport is
just JSON-RPC 2.0, one JSON object per line. The whole client is ~150 lines:

```java
request("initialize", initParams);                 // handshake
notification("notifications/initialized", empty);
JsonNode result = request("tools/list", empty);    // discover tools
// later: request("tools/call", {name, arguments})
```

Each discovered tool is wrapped so it looks exactly like a built-in:

```java
registry.register(new McpTool(client, serverName, def));  // appears as server__tool
```

The agent loop doesn't care whether a tool is native Java or a remote MCP
server — the `Tool` interface hides the difference. I tested the whole handshake
against an in-memory fake server over piped streams, no subprocess needed.

## 3. Parallel tools — for free, safely

Models often request several reads at once ("read these four files"). Running
them serially is wasted wall-clock. When *every* call in a turn is read-only, run
them on virtual threads:

```java
boolean parallel = calls.size() > 1 && calls.stream().allMatch(this::isReadOnly);
try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = calls.stream().map(c -> pool.submit(() -> dispatch(c))).toList();
    // collect in original order…
}
```

The "all read-only" guard matters: mutating tools go through an *interactive*
permission prompt, and you can't prompt for two things at once on one terminal.
So writes stay serial; reads fan out. Virtual threads (Java 21+) make this almost
free — no pool tuning, thousands are fine.

## The throughline

None of these required touching the core loop. Subagents reuse `Agent`; MCP tools
reuse `Tool`; parallelism wraps `dispatch`. That's the payoff of small interfaces
and honest seams: advanced features become *compositions* of what's already there.

Read the whole thing — ~30 small classes, 41 tests:
https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

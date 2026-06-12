---
title: "Building a Coding Agent in Java, Part 2: Tools and Permissions"
published: false
description: "A clean Tool interface, a registry that becomes the model's API, and a permission gate so the agent can't wreck your repo. Java, from scratch."
tags: java, ai, llm, tutorial
series: "Building a coding agent in Java"
canonical_url:
cover_image:
---

> Part 2 of building **Javuk**, a terminal coding agent in Java.
> [Part 1: the agent loop](01-the-agent-loop.md). Code:
> https://github.com/UsmanovMahmudkhan/codecrafters-claude-code-java

In [part 1](01-the-agent-loop.md) the loop dispatched tool calls to a registry.
Now let's design that registry — and make it safe.

## One interface for every tool

```java
public interface Tool {
    String name();
    String description();
    Map<String, Object> properties();   // JSON-schema for the arguments
    List<String> required();
    default boolean mutating() { return false; }   // writes files / runs commands?
    String execute(JsonNode args, ToolContext ctx) throws Exception;
}
```

A tool advertises its arguments as a JSON schema (so the model knows how to call
it) and runs against a `ToolContext` carrying the working directory and the
permission gate. `Read` is read-only; `Write`, `Edit`, and `Bash` are `mutating`.

## The registry *is* the model's API

`ToolRegistry` does two jobs: turn each tool into an OpenAI function spec, and
dispatch calls by name.

```java
public String dispatch(String name, String rawArgs, ToolContext ctx) {
    Tool tool = tools.get(name);
    if (tool == null) return "Error: unknown tool '" + name + "'";

    JsonNode args = Json.parse(rawArgs);

    if (tool.mutating()
            && !ctx.permissions().allow(name, true, summarize(name, args))) {
        return "Permission denied by user.";
    }
    try {
        return tool.execute(args, ctx);
    } catch (Exception e) {
        return "Error executing " + name + ": " + e.getMessage();
    }
}
```

Crucially, `dispatch` **never throws**. Unknown tool, denied permission, blown-up
execution — all become *strings the model reads*. The agent then adapts ("the
file wasn't found, let me list the directory") instead of crashing. Errors are
data, not exceptions.

## The tools worth building

The starter had Read/Write/Bash. The ones that make an agent feel competent:

- **Edit** — exact-string replace with a *uniqueness guard*: if `old_string`
  appears more than once, refuse unless `replace_all` is set. This single rule
  prevents a whole class of "it edited the wrong line" bugs.
- **Glob / Grep** — let the model *find* code instead of guessing paths.
- **MultiEdit** — several edits to one file, all-or-nothing.

Here's the uniqueness guard:

```java
int count = countOccurrences(content, oldStr);
if (count == 0)               return "Error: old_string not found";
if (count > 1 && !replaceAll) return "Error: old_string occurs " + count
                                     + " times. Make it unique or pass replace_all.";
```

## Permissions: a one-method gate

Mutating tools have to clear a gate before acting:

```java
public interface PermissionService {
    boolean allow(String toolName, boolean mutating, String description);
}
```

Three behaviours cover everything:

- **ask** — prompt before each change (allow once / allow all this session / deny);
- **auto** (`--yolo`) — allow everything;
- **plan** — read-only: allow reads, block every write and command.

Because the gate lives in `dispatch`, *every* tool is covered automatically and
new tools inherit safety for free. "Plan mode" is one line: `return !mutating;`.

## Testing tools is easy

Tools are pure-ish — give them a temp dir and assert on the filesystem:

```java
@Test
void rejectsAmbiguousEdit(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("a.txt"), "x x x");
    String out = new EditTool().execute(
        Json.parse("{\"file_path\":\"a.txt\",\"old_string\":\"x\",\"new_string\":\"y\"}"),
        new ToolContext(dir, PermissionService.allowAll()));
    assertTrue(out.contains("occurs 3 times"));
}
```

## Next up

In [part 3](03-the-repl-and-streaming.md) we make it feel alive: a JLine REPL,
token-by-token streaming, and accumulating tool calls out of a stream.

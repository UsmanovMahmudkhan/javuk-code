# Contributing to Javuk

Thanks for your interest! Javuk is a learning-friendly codebase — small classes,
clear seams, and tests for everything.

## Setup

- JDK 25 and Maven.
- `mvn -B package` compiles, runs tests, and assembles the jar.
- `mvn -B test` runs just the test suite.

## Adding a tool

1. Implement [`Tool`](src/main/java/dev/javuk/tools/Tool.java) in `dev.javuk.tools`.
2. Advertise your arguments via `properties()` + `required()`.
3. Mark `mutating()` `true` if it writes files or runs commands (so it goes
   through the permission gate).
4. Register it in [`Tools.defaultRegistry()`](src/main/java/dev/javuk/tools/Tools.java).
5. Add a JUnit test using `@TempDir` (see existing tool tests for the pattern).

## Adding a provider

All current providers are OpenAI-compatible, so usually you only need a new
entry in [`Provider`](src/main/java/dev/javuk/llm/Provider.java). A genuinely
different protocol means a new `LlmClient` implementation returning `AssistantTurn`.

## Style

- Match the surrounding code: small classes, javadoc on public types, no dead code.
- Keep `Main`'s `-p` behaviour intact (the CodeCrafters contract).
- Run `mvn -B test` before opening a PR — CI must stay green.

## Commit & PR

- One focused change per PR with a clear description.
- Include or update tests for behaviour changes.

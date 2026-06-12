# Project context for Javuk

This file is loaded into Javuk's system prompt when it runs in this repo.

- **Project:** Javuk — a terminal coding agent written in Java 25.
- **Build:** `mvn -B package` (compiles, tests, assembles the jar). Run with `--enable-preview`.
- **Tests:** `mvn -B test`. Tools are tested with JUnit 5 `@TempDir`.
- **Layout:** code under `src/main/java/dev/javuk/{agent,llm,tools,permission,config,session,ui,util,cli}`.
  `Main` (default package) is a thin shim — keep its `-p` behaviour intact (CodeCrafters contract).
- **Conventions:** small single-responsibility classes, javadoc on public types,
  no dead code. New tools implement `Tool` and register in `Tools.defaultRegistry()`.

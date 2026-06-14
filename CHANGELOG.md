# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added
- **Agent personas:** selectable agents with their own system prompt, restricted
  tool set, and optional model. Four built-ins (`code-reviewer`, `planner`,
  `test-writer`, `explorer`) plus user-defined `.javuk/agents/*.md` and
  `~/.config/javuk/agents/*.md` (project overrides built-ins by name).
- **`/agents` command:** list personas and switch the session to one
  (`/agents default` resets); the `Task` tool accepts a `subagent_type` so the
  model can delegate to an agent that runs with only that agent's tools.
- **Notification sounds:** audible cues on turn completion, permission prompts, and
  errors via the platform audio player with a terminal-bell fallback. Toggle with
  `/sound`, `--no-sound`, the `sound` config key, or the `NO_SOUND` env var; silent
  when output is not a TTY.

## [1.1.0]

A major capability upgrade focused on what makes an agent "advanced".

### Added
- **Subagents:** the `Task` tool delegates a subtask to a nested agent.
- **MCP client:** connect Model Context Protocol servers over stdio; their tools
  register as `server__tool` (config: `mcpServers`).
- **Parallel tool execution:** independent read-only calls run on virtual threads.
- **Diff previews:** Edit/Write/MultiEdit show a coloured diff in the permission prompt.
- **Persistent allowlist:** `/allow <pattern>` / `/allowed`, saved to `~/.config/javuk`.
- **Hooks:** pre/post-tool shell commands (config: `hooks`); a failing pre-hook blocks the tool.
- **Custom slash commands:** `.javuk/commands/*.md` templates with `$ARGUMENTS`.
- **@-file mentions:** `@path` in a prompt inlines that file's contents.
- **Cancellable turns:** Ctrl-C aborts the running turn instead of quitting.
- **/compact:** summarizes the conversation to reclaim context.
- 10 more JUnit tests (41 total), including an in-memory MCP JSON-RPC test.

## [1.0.0]

The single-file CodeCrafters starter grew into a full agent platform.

### Added
- Clean package architecture under `dev.javuk` (`agent`, `llm`, `tools`,
  `permission`, `config`, `session`, `ui`, `cli`).
- Interactive JLine REPL with history, slash commands, banner, and spinner.
- Live token streaming with tool-call accumulation.
- Tool suite: Read, Write, Edit, MultiEdit, Bash, Glob, Grep, List, TodoWrite, WebFetch.
- Permission system: `ask` / `auto` (`--yolo`) / `plan` (read-only).
- System prompt with project-context loading (`JAVUK.md` / `AGENTS.md` / `CLAUDE.md`).
- Multi-provider support (OpenRouter, OpenAI, Ollama) via `--provider`.
- Config precedence: defaults < config file < environment < CLI flags.
- Session save / load / list / resume (`--resume`).
- Token + cost tracking (`/cost`, `/tokens`).
- Request retries with backoff, timeouts, and optional debug logging.
- 31 JUnit tests and a GitHub Actions CI workflow.

### Preserved
- The CodeCrafters one-shot contract: `-p <prompt>` prints the final answer to stdout.

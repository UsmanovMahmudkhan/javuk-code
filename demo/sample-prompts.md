# Sample prompts

Good prompts for a demo recording — each exercises a different capability.

1. **Explore** — *"What does this project do? Give me a 3-line summary."*
   (uses Read / Glob / Grep)

2. **Edit + verify** — *"Add a `--version` note to the README and run `mvn -q -B test`."*
   (uses Read, Edit, Bash)

3. **Multi-step** — *"Add a new tool `Pwd` that prints the working directory, register it, and add a test."*
   (uses TodoWrite, Write, Edit, Bash — a full feature in one turn)

4. **Read-only safety** — run with `--plan` and ask it to delete a file; watch it refuse.

5. **Sessions** — after a conversation, `/save demo`, quit, then
   `--resume demo` and ask *"what were we doing?"*

One-shot (great for screenshots):

```sh
java --enable-preview -jar target/codecrafters-claude-code.jar \
  -p "create demo/hello.txt containing a friendly greeting, then read it back"
```

# Demo

Material for showing Javuk off in the README and blog posts.

## Record an asciinema cast

[asciinema](https://asciinema.org/) produces a lightweight, embeddable terminal
recording (much better than a GIF for a CLI).

```sh
# install: brew install asciinema   (or: pipx install asciinema)
export OPENROUTER_API_KEY="sk-or-..."
mvn -B package
asciinema rec demo/javuk.cast -c "./record.sh"
# then: asciinema upload demo/javuk.cast   → paste the link in the README
```

`record.sh` launches the REPL so you can type the [sample prompts](sample-prompts.md).

## Make a GIF instead

```sh
# install: cargo install --git https://github.com/asciinema/agg
agg demo/javuk.cast demo/javuk.gif
```

Drop `javuk.gif` into the README hero once recorded.

package dev.javuk.ui;

/** The startup banner shown when the REPL launches. */
public final class Banner {

    private static final String ART = """
              _                   _
             | | __ ___   __ _   _| | __
          _  | |/ _` \\ \\ / / | | | |/ /
         | |_| | (_| |\\ V /| |_| |   <
          \\___/ \\__,_| \\_/  \\__,_|_|\\_\\
        """;

    private Banner() {
    }

    public static String render(String model, String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(Ansi.cyan(ART));
        sb.append(Ansi.bold("  Javuk")).append(Ansi.dim(" — a coding agent in Java")).append('\n');
        sb.append(Ansi.gray("  model: ")).append(model)
                .append(Ansi.gray("   mode: ")).append(mode).append('\n');
        sb.append(Ansi.gray("  type /help for commands, /exit to quit")).append('\n');
        return sb.toString();
    }
}

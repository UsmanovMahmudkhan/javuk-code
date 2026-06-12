import dev.javuk.cli.JavukCli;

/**
 * Entry point. Kept in the default package and named {@code Main} to satisfy the
 * CodeCrafters jar manifest contract; all real logic lives under {@code dev.javuk}.
 */
public class Main {
    public static void main(String[] args) {
        // Logs from the program appear on stderr; stdout is reserved for the answer.
        System.err.println("Logs from your program will appear here!");
        int exitCode = new JavukCli().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}

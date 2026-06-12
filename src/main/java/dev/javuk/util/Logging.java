package dev.javuk.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Minimal append-only file logger at {@code ~/.config/javuk/javuk.log}. Logging
 * is best-effort: failures to write are swallowed so they never disrupt the
 * agent. Enabled only when {@code JAVUK_DEBUG} is set.
 */
public final class Logging {

    private static final boolean ENABLED = System.getenv("JAVUK_DEBUG") != null;
    private static final Path LOG_FILE =
            Path.of(System.getProperty("user.home"), ".config", "javuk", "javuk.log");

    private Logging() {
    }

    public static void debug(String message) {
        if (!ENABLED) {
            return;
        }
        write("DEBUG", message);
    }

    public static void error(String message, Throwable t) {
        write("ERROR", message + (t != null ? ": " + t : ""));
    }

    private static synchronized void write(String level, String message) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE,
                    Instant.now() + " [" + level + "] " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging must never break the agent
        }
    }
}

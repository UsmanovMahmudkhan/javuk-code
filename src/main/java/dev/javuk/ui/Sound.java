package dev.javuk.ui;

import java.util.concurrent.TimeUnit;

/**
 * Audible notifications for REPL events (turn complete, permission prompt, error).
 * Disabled automatically when output is not a TTY or when the {@code NO_SOUND}
 * environment variable is set, mirroring {@link Ansi}'s gating, and gated further
 * by a runtime toggle ({@code /sound}, {@code --no-sound}, config).
 *
 * <p>Plays via the platform's audio player ({@code afplay} on macOS,
 * {@code paplay} on Linux, PowerShell on Windows) on a short-lived background
 * thread so it never blocks the REPL; if no player is available it falls back to
 * the terminal bell ({@code \007}). All failures are swallowed.
 */
public final class Sound {

    public enum Event {TURN_COMPLETE, PERMISSION, ERROR}

    private static final boolean TTY =
            System.getenv("NO_SOUND") == null && System.console() != null;
    private static final String OS = System.getProperty("os.name", "").toLowerCase();

    private static volatile boolean enabled = true;

    private Sound() {
    }

    /** Sets the master toggle (from config at startup). */
    public static void configure(boolean on) {
        enabled = on;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Plays the cue for {@code event}, or does nothing if sound is off / not a TTY. */
    public static void play(Event event) {
        if (!enabled || !TTY || event == null) {
            return;
        }
        Thread t = new Thread(() -> playBlocking(event), "javuk-sound");
        t.setDaemon(true);
        t.start();
    }

    private static void playBlocking(Event event) {
        String[] command = command(event);
        if (command != null) {
            try {
                Process p = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return;
                }
                p.destroy();
            } catch (Exception ignored) {
                // fall through to the bell
            }
        }
        bell();
    }

    private static String[] command(Event event) {
        if (OS.contains("mac") || OS.contains("darwin")) {
            return new String[]{"afplay", "/System/Library/Sounds/" + macSound(event) + ".aiff"};
        }
        if (OS.contains("nux") || OS.contains("nix")) {
            return new String[]{"paplay", linuxSound(event)};
        }
        if (OS.contains("win")) {
            return new String[]{"powershell", "-NoProfile", "-Command", winSound(event)};
        }
        return null; // unknown OS → bell fallback
    }

    private static String macSound(Event event) {
        return switch (event) {
            case TURN_COMPLETE -> "Glass";
            case PERMISSION -> "Submarine";
            case ERROR -> "Basso";
        };
    }

    private static String linuxSound(Event event) {
        String base = "/usr/share/sounds/freedesktop/stereo/";
        return base + switch (event) {
            case TURN_COMPLETE -> "complete.oga";
            case PERMISSION -> "message.oga";
            case ERROR -> "dialog-error.oga";
        };
    }

    private static String winSound(Event event) {
        int freq = switch (event) {
            case TURN_COMPLETE -> 880;
            case PERMISSION -> 660;
            case ERROR -> 330;
        };
        return "[console]::beep(" + freq + ",200)";
    }

    private static void bell() {
        try {
            System.out.write('\007');
            System.out.flush();
        } catch (Exception ignored) {
            // nothing else to do
        }
    }
}

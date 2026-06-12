package dev.javuk.ui;

import java.io.PrintStream;

/**
 * A tiny animated spinner shown while waiting for the model's first token.
 * Runs on a daemon thread and erases itself when stopped. No-ops when output is
 * not a TTY so logs stay clean.
 */
public final class Spinner {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final PrintStream out;
    private final String label;
    private volatile boolean running;
    private Thread thread;

    public Spinner(PrintStream out, String label) {
        this.out = out;
        this.label = label;
    }

    public void start() {
        if (!Ansi.enabled()) {
            return;
        }
        running = true;
        thread = new Thread(() -> {
            int i = 0;
            while (running) {
                out.print("\r" + Ansi.cyan(FRAMES[i % FRAMES.length]) + " " + Ansi.dim(label));
                out.flush();
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the spinner and clears its line. */
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (Ansi.enabled()) {
            out.print("\r[2K"); // carriage return + clear entire line
            out.flush();
        }
    }
}

package dev.javuk.ui;

/**
 * Minimal ANSI styling. Colour is disabled automatically when output is not a
 * TTY or when the {@code NO_COLOR} environment variable is set, so piped output
 * stays clean.
 */
public final class Ansi {

    private static final boolean ENABLED =
            System.getenv("NO_COLOR") == null && System.console() != null;

    private static final String CSI = "[";
    private static final String RESET = CSI + "0m";

    private Ansi() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    private static String wrap(int code, String s) {
        return ENABLED ? CSI + code + "m" + s + RESET : s;
    }

    public static String bold(String s) {
        return wrap(1, s);
    }

    public static String dim(String s) {
        return wrap(2, s);
    }

    public static String red(String s) {
        return wrap(31, s);
    }

    public static String green(String s) {
        return wrap(32, s);
    }

    public static String yellow(String s) {
        return wrap(33, s);
    }

    public static String blue(String s) {
        return wrap(34, s);
    }

    public static String magenta(String s) {
        return wrap(35, s);
    }

    public static String cyan(String s) {
        return wrap(36, s);
    }

    public static String gray(String s) {
        return wrap(90, s);
    }
}

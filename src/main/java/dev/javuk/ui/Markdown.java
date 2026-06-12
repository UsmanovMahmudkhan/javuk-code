package dev.javuk.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very small Markdown→ANSI renderer for terminal output: headings, bold,
 * inline code, and fenced code blocks. Anything it doesn't recognise passes
 * through unchanged, so it never mangles plain text.
 */
public final class Markdown {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");

    private Markdown() {
    }

    public static String render(String markdown) {
        if (!Ansi.enabled()) {
            return markdown;
        }
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                out.append(Ansi.gray(line)).append('\n');
                continue;
            }
            if (inFence) {
                out.append(Ansi.dim(line)).append('\n');
                continue;
            }
            out.append(renderInline(line)).append('\n');
        }
        // Drop the trailing newline we added for the final (often empty) split element.
        if (!out.isEmpty() && out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static String renderInline(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("#")) {
            return Ansi.bold(Ansi.cyan(line.replaceFirst("^#+\\s*", "")));
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            String indent = line.substring(0, line.length() - trimmed.length());
            return indent + Ansi.yellow("•") + " " + applyInlineStyles(trimmed.substring(2));
        }
        return applyInlineStyles(line);
    }

    private static String applyInlineStyles(String text) {
        Matcher code = INLINE_CODE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (code.find()) {
            code.appendReplacement(sb, Matcher.quoteReplacement(Ansi.yellow(code.group(1))));
        }
        code.appendTail(sb);

        Matcher bold = BOLD.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (bold.find()) {
            bold.appendReplacement(out, Matcher.quoteReplacement(Ansi.bold(bold.group(1))));
        }
        bold.appendTail(out);
        return out.toString();
    }
}

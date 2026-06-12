package dev.javuk.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code @path} mentions in a user prompt: any {@code @<path>} that
 * resolves to a readable file under the working directory has its contents
 * appended to the prompt as context. The original text is preserved.
 */
public final class Mentions {

    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([\\w./~-]+)");
    private static final int MAX_FILE_CHARS = 20_000;

    private Mentions() {
    }

    public static String expand(String prompt, Path workingDir) {
        Matcher m = MENTION.matcher(prompt);
        Set<String> paths = new LinkedHashSet<>();
        while (m.find()) {
            paths.add(m.group(1));
        }
        if (paths.isEmpty()) {
            return prompt;
        }

        StringBuilder appendix = new StringBuilder();
        for (String ref : paths) {
            Path p = workingDir.resolve(ref).normalize();
            if (Files.isRegularFile(p)) {
                try {
                    String body = Files.readString(p);
                    if (body.length() > MAX_FILE_CHARS) {
                        body = body.substring(0, MAX_FILE_CHARS) + "\n…[truncated]";
                    }
                    appendix.append("\n\nContents of ").append(ref).append(":\n```\n")
                            .append(body).append("\n```");
                } catch (Exception ignored) {
                    // unreadable mention — leave it as plain text
                }
            }
        }
        return appendix.isEmpty() ? prompt : prompt + appendix;
    }
}

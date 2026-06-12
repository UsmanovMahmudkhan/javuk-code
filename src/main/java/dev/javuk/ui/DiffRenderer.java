package dev.javuk.ui;

import java.util.List;

/**
 * Renders a compact, coloured unified diff between two texts using a
 * longest-common-subsequence line diff. Used to preview file changes before the
 * permission gate approves them.
 */
public final class DiffRenderer {

    private static final int MAX_LINES = 80;

    private DiffRenderer() {
    }

    public static String diff(String before, String after) {
        List<String> a = before.isEmpty() ? List.of() : List.of(before.split("\n", -1));
        List<String> b = after.isEmpty() ? List.of() : List.of(after.split("\n", -1));

        int[][] lcs = lcsTable(a, b);
        StringBuilder sb = new StringBuilder();
        int[] counts = {0, 0, 0}; // added, removed, emitted-lines
        emit(sb, a, b, lcs, counts);

        String header = Ansi.gray("  ~" + counts[0] + " added, ~" + counts[1] + " removed");
        if (counts[2] >= MAX_LINES) {
            header += Ansi.gray(" (diff truncated)");
        }
        return header + "\n" + sb;
    }

    private static int[][] lcsTable(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                dp[i][j] = a.get(i).equals(b.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
        return dp;
    }

    private static void emit(StringBuilder sb, List<String> a, List<String> b,
                             int[][] lcs, int[] counts) {
        // Reconstruct the diff from the front using the LCS table.
        int fi = 0, fj = 0;
        while (fi < a.size() && fj < b.size()) {
            if (counts[2] >= MAX_LINES) {
                return;
            }
            if (a.get(fi).equals(b.get(fj))) {
                sb.append(Ansi.gray("   " + a.get(fi))).append('\n');
                fi++;
                fj++;
            } else if (lcs[fi + 1][fj] >= lcs[fi][fj + 1]) {
                sb.append(Ansi.red(" - " + a.get(fi))).append('\n');
                counts[1]++;
                fi++;
            } else {
                sb.append(Ansi.green(" + " + b.get(fj))).append('\n');
                counts[0]++;
                fj++;
            }
            counts[2]++;
        }
        while (fi < a.size() && counts[2] < MAX_LINES) {
            sb.append(Ansi.red(" - " + a.get(fi++))).append('\n');
            counts[1]++;
            counts[2]++;
        }
        while (fj < b.size() && counts[2] < MAX_LINES) {
            sb.append(Ansi.green(" + " + b.get(fj++))).append('\n');
            counts[0]++;
            counts[2]++;
        }
    }
}

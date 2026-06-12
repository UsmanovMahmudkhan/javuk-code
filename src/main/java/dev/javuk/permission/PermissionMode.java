package dev.javuk.permission;

/** How the agent treats mutating tool calls. */
public enum PermissionMode {
    /** Prompt the user before each mutating action. */
    ASK,
    /** Allow every action without prompting ({@code --yolo}). */
    AUTO,
    /** Block all mutating actions; only reads are allowed (plan / readonly). */
    PLAN;

    public static PermissionMode from(String s) {
        if (s == null) {
            return ASK;
        }
        return switch (s.toLowerCase()) {
            case "auto", "yolo" -> AUTO;
            case "plan", "readonly", "read-only" -> PLAN;
            default -> ASK;
        };
    }
}

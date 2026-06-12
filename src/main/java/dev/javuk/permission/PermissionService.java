package dev.javuk.permission;

/**
 * Gate that mutating tools (Write, Edit, Bash, ...) must clear before acting.
 * Implementations decide based on the active {@link PermissionMode} and may
 * prompt the user interactively.
 */
public interface PermissionService {

    /**
     * @param toolName    the tool requesting permission (e.g. "Bash")
     * @param mutating    whether the action changes state (writes / runs commands)
     * @param description short human-readable summary of what will happen
     * @return true if the action is permitted
     */
    boolean allow(String toolName, boolean mutating, String description);

    /** Allows everything — used for one-shot/CodeCrafters runs and {@code --yolo}. */
    static PermissionService allowAll() {
        return (tool, mutating, desc) -> true;
    }

    /** Permits read-only tools, blocks every mutating action (plan / readonly mode). */
    static PermissionService readOnly() {
        return (tool, mutating, desc) -> !mutating;
    }
}

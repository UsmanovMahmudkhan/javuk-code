package dev.javuk.permission;

import java.util.function.Function;

/**
 * Permission gate driven by the active {@link PermissionMode}. In {@code ASK}
 * mode it prompts the user for each mutating action via the supplied prompter;
 * the user may allow once, allow all for the rest of the session, or deny.
 */
public final class InteractivePermissionService implements PermissionService {

    /** Asks the user a question and returns their typed answer (may be null). */
    public interface Prompter {
        String ask(String question);
    }

    private PermissionMode mode;
    private final Prompter prompter;
    private final AllowList allowList;
    private boolean allowAllForSession;

    public InteractivePermissionService(PermissionMode mode, Prompter prompter) {
        this(mode, prompter, new AllowList());
    }

    public InteractivePermissionService(PermissionMode mode, Prompter prompter, AllowList allowList) {
        this.mode = mode;
        this.prompter = prompter;
        this.allowList = allowList;
    }

    public AllowList allowList() {
        return allowList;
    }

    public PermissionMode mode() {
        return mode;
    }

    public void setMode(PermissionMode mode) {
        this.mode = mode;
        if (mode != PermissionMode.ASK) {
            allowAllForSession = false;
        }
    }

    @Override
    public boolean allow(String toolName, boolean mutating, String description) {
        if (!mutating) {
            return true;
        }
        return switch (mode) {
            case AUTO -> true;
            case PLAN -> false;
            case ASK -> askUser(toolName, description);
        };
    }

    private boolean askUser(String toolName, String description) {
        if (allowAllForSession || allowList.allows(toolName, description)) {
            return true;
        }
        dev.javuk.ui.Sound.play(dev.javuk.ui.Sound.Event.PERMISSION);
        String answer = prompter.ask(toolName + " wants to " + description
                + "\n  [y] allow once  [a] allow all this session  [n] deny: ");
        if (answer == null) {
            return false;
        }
        answer = answer.strip().toLowerCase();
        if (answer.equals("a")) {
            allowAllForSession = true;
            return true;
        }
        return answer.equals("y") || answer.equals("yes");
    }
}

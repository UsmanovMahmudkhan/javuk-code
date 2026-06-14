package dev.javuk.tools;

/** Factory for the standard Javuk tool set, shared by one-shot and REPL modes. */
public final class Tools {

    private Tools() {
    }

    /** A registry pre-loaded with every built-in tool, with secure defaults. */
    public static ToolRegistry defaultRegistry() {
        return defaultRegistry(false);
    }

    /**
     * A registry pre-loaded with every built-in tool.
     *
     * @param allowPrivateFetch if true, WebFetch may target private/internal hosts
     */
    public static ToolRegistry defaultRegistry(boolean allowPrivateFetch) {
        return new ToolRegistry()
                .register(new ReadTool())
                .register(new WriteTool())
                .register(new EditTool())
                .register(new MultiEditTool())
                .register(new BashTool())
                .register(new GlobTool())
                .register(new GrepTool())
                .register(new ListTool())
                .register(new TodoWriteTool())
                .register(new WebFetchTool(allowPrivateFetch));
    }
}

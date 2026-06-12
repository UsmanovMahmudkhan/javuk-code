package dev.javuk.llm;

/**
 * Running tally of token usage across a session. Cost estimation uses the
 * pricing table in {@link ModelCatalog}.
 */
public final class Usage {

    private long promptTokens;
    private long completionTokens;
    private int requests;

    public synchronized void add(long prompt, long completion) {
        this.promptTokens += prompt;
        this.completionTokens += completion;
        this.requests++;
    }

    public synchronized long promptTokens() {
        return promptTokens;
    }

    public synchronized long completionTokens() {
        return completionTokens;
    }

    public synchronized long totalTokens() {
        return promptTokens + completionTokens;
    }

    public synchronized int requests() {
        return requests;
    }

    /** Estimated cost in USD for the given model, or -1 if pricing is unknown. */
    public synchronized double estimatedCostUsd(String model) {
        return ModelCatalog.estimateCost(model, promptTokens, completionTokens);
    }
}

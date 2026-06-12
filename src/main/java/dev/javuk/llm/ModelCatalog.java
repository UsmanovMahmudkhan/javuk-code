package dev.javuk.llm;

import java.util.Map;

/**
 * Small, best-effort pricing table (USD per 1M tokens) for cost estimation.
 * Unknown models simply return -1 so the UI can show "n/a" rather than a wrong
 * number. Prices are approximate and easy to update in one place.
 */
public final class ModelCatalog {

    private record Price(double inputPerM, double outputPerM) {
    }

    // Approximate public list prices, USD per 1,000,000 tokens.
    private static final Map<String, Price> PRICES = Map.ofEntries(
            Map.entry("anthropic/claude-haiku-4.5", new Price(1.00, 5.00)),
            Map.entry("anthropic/claude-sonnet-4.6", new Price(3.00, 15.00)),
            Map.entry("anthropic/claude-opus-4.8", new Price(15.00, 75.00)),
            Map.entry("openai/gpt-4o-mini", new Price(0.15, 0.60)),
            Map.entry("openai/gpt-4o", new Price(2.50, 10.00))
    );

    private ModelCatalog() {
    }

    /** Model ids with known pricing — shown by the {@code /models} command. */
    public static java.util.Set<String> known() {
        return PRICES.keySet();
    }

    /** @return estimated USD cost, or -1 if the model's pricing is unknown. */
    public static double estimateCost(String model, long promptTokens, long completionTokens) {
        Price p = PRICES.get(model);
        if (p == null) {
            return -1;
        }
        return promptTokens / 1_000_000.0 * p.inputPerM()
                + completionTokens / 1_000_000.0 * p.outputPerM();
    }
}

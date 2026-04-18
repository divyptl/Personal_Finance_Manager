package com.example.personalfinancemanager;

/**
 * Derived (non-Room) POJO representing a detected recurring charge.
 * Computed on demand by {@link SubscriptionDetector} from the transaction
 * history — we deliberately do NOT persist these so that edits to historical
 * transactions automatically flow through to the detected list.
 */
public final class Subscription {

    /** Cadence bucket. */
    public enum Cadence {
        WEEKLY("Weekly"),
        MONTHLY("Monthly"),
        QUARTERLY("Quarterly"),
        ANNUAL("Annual");

        private final String label;
        Cadence(String label) { this.label = label; }
        public String label() { return label; }
    }

    /** Human-readable merchant name (normalized from the SMS body). */
    public final String merchant;
    /** Median charge amount, in ₹. */
    public final double amount;
    /** Detected cadence. */
    public final Cadence cadence;
    /** Number of occurrences observed. */
    public final int occurrences;
    /** Timestamp (ms) of the most recent occurrence. */
    public final long lastChargeMillis;
    /** Category of the most recent occurrence. */
    public final String category;

    public Subscription(String merchant, double amount, Cadence cadence,
                        int occurrences, long lastChargeMillis, String category) {
        this.merchant = merchant;
        this.amount = amount;
        this.cadence = cadence;
        this.occurrences = occurrences;
        this.lastChargeMillis = lastChargeMillis;
        this.category = category;
    }

    /** Projected monthly cost for the "Estimated monthly outgo" summary. */
    public double monthlyEquivalent() {
        switch (cadence) {
            case WEEKLY:    return amount * 4.33;
            case MONTHLY:   return amount;
            case QUARTERLY: return amount / 3.0;
            case ANNUAL:    return amount / 12.0;
            default:        return amount;
        }
    }
}

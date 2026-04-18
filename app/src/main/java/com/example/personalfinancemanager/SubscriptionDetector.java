package com.example.personalfinancemanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-function detector for recurring charges.
 *
 * <p>Algorithm (deliberately conservative — false positives are worse than
 * false negatives in a "what am I paying for every month?" view):
 *
 * <ol>
 *   <li>Filter to expense transactions only.</li>
 *   <li>Group by a {@link #merchantKey(String) normalized merchant key}
 *       extracted from the SMS body.</li>
 *   <li>For each group with ≥ 2 occurrences, sort by timestamp and compute
 *       the median inter-charge interval in days. If the median falls into a
 *       recognized cadence bucket AND the amounts cluster within ±15% of the
 *       median amount, emit a {@link Subscription}.</li>
 * </ol>
 *
 * No Android dependencies — unit-testable in milliseconds.
 */
public final class SubscriptionDetector {

    private SubscriptionDetector() {}

    private static final long DAY_MS = 86_400_000L;
    private static final double AMOUNT_TOLERANCE = 0.15; // ±15%

    public static List<Subscription> detect(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return Collections.emptyList();

        // Bucket by (merchant, ~amount) — amount bucketing avoids merging a
        // Netflix ₹199 subscription with a one-off Netflix gift card topup.
        Map<String, List<Transaction>> buckets = new HashMap<>();
        for (Transaction t : transactions) {
            String type = t.getType();
            if (type == null) continue;
            if (!(type.equalsIgnoreCase("expense") || type.equalsIgnoreCase("Debit"))) continue;
            if (t.getAmount() <= 0) continue;

            String merchant = merchantKey(t.getMessage());
            if (merchant == null || merchant.isEmpty()) continue;

            // Round amount to nearest ₹10 so ₹199 and ₹199.99 bucket together.
            long amountBucket = Math.round(t.getAmount() / 10.0);
            String key = merchant + "|" + amountBucket;
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<Subscription> out = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> e : buckets.entrySet()) {
            List<Transaction> group = e.getValue();
            if (group.size() < 2) continue;

            Collections.sort(group, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            // Amount consistency check.
            double medAmount = median(amounts(group));
            if (!amountsConsistent(group, medAmount)) continue;

            // Interval consistency check.
            List<Long> gapsDays = new ArrayList<>();
            for (int i = 1; i < group.size(); i++) {
                long gap = group.get(i).getTimestamp() - group.get(i - 1).getTimestamp();
                gapsDays.add(Math.round((double) gap / DAY_MS));
            }
            long medGap = Math.round(medianLong(gapsDays));
            Subscription.Cadence cadence = cadenceFor(medGap);
            if (cadence == null) continue;

            Transaction latest = group.get(group.size() - 1);
            String merchantLabel = merchantKey(latest.getMessage());
            out.add(new Subscription(
                    prettyMerchant(merchantLabel),
                    medAmount,
                    cadence,
                    group.size(),
                    latest.getTimestamp(),
                    latest.getCategory() != null ? latest.getCategory() : "Other"));
        }

        // Sort highest monthly-equivalent cost first — most useful default.
        Collections.sort(out,
                (a, b) -> Double.compare(b.monthlyEquivalent(), a.monthlyEquivalent()));
        return out;
    }

    // ---------- Merchant extraction ----------

    private static final Pattern MERCHANT_HINT = Pattern.compile(
            "(?i)(?:at|to|towards|info|info:|for|on)\\s+([A-Za-z][A-Za-z0-9 &._-]{2,40})");
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    /**
     * Extract a normalized merchant token from a bank SMS body. Strategy:
     * <ul>
     *   <li>Look for "at X", "to X", "info: X" style hints.</li>
     *   <li>Fall back to the longest all-caps run in the body (most bank
     *       SMS put the merchant in SHOUT CASE).</li>
     * </ul>
     * Output is uppercased, with non-alphanumerics stripped — suitable as a
     * bucket key.
     */
    static String merchantKey(String body) {
        if (body == null) return "";
        Matcher m = MERCHANT_HINT.matcher(body);
        if (m.find()) {
            return normalize(m.group(1));
        }

        // Fallback: longest SHOUT-CASE run.
        String best = "";
        Matcher caps = Pattern.compile("[A-Z][A-Z0-9 &._-]{3,40}").matcher(body);
        while (caps.find()) {
            String s = caps.group().trim();
            if (s.length() > best.length()) best = s;
        }
        return normalize(best);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String upper = s.toUpperCase(Locale.ROOT);
        String stripped = NON_ALNUM.matcher(upper).replaceAll("");
        // Drop leading tokens that look like account refs (numeric noise).
        // Keep only the alphabetic portion's first ~24 chars.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stripped.length() && sb.length() < 24; i++) {
            char c = stripped.charAt(i);
            if (Character.isLetter(c) || (sb.length() > 0 && Character.isDigit(c))) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String prettyMerchant(String normalized) {
        if (normalized == null || normalized.isEmpty()) return "Unknown";
        // Title-case the normalized key for display.
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ---------- Cadence buckets ----------

    private static Subscription.Cadence cadenceFor(long days) {
        if (days >= 5 && days <= 9)    return Subscription.Cadence.WEEKLY;
        if (days >= 25 && days <= 35)  return Subscription.Cadence.MONTHLY;
        if (days >= 85 && days <= 95)  return Subscription.Cadence.QUARTERLY;
        if (days >= 355 && days <= 375) return Subscription.Cadence.ANNUAL;
        return null;
    }

    // ---------- Small math helpers ----------

    private static List<Double> amounts(List<Transaction> txns) {
        List<Double> out = new ArrayList<>(txns.size());
        for (Transaction t : txns) out.add(t.getAmount());
        return out;
    }

    private static boolean amountsConsistent(List<Transaction> txns, double median) {
        if (median <= 0) return false;
        for (Transaction t : txns) {
            double delta = Math.abs(t.getAmount() - median) / median;
            if (delta > AMOUNT_TOLERANCE) return false;
        }
        return true;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) return sorted.get(n / 2);
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double medianLong(List<Long> values) {
        List<Double> asDouble = new ArrayList<>(values.size());
        for (Long v : values) asDouble.add((double) v);
        return median(asDouble);
    }
}

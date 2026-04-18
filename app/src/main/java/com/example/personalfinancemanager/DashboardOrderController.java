package com.example.personalfinancemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists and applies the user's preferred order of the four dashboard
 * cards: balance, pie chart, quick-action buttons, recent transactions.
 *
 * <p>The four section views are siblings inside a vertical LinearLayout
 * inside activity_main. Reordering = removing each from its parent and
 * re-adding in the desired index. LinearLayout re-measures automatically;
 * no recycler or adapter required.
 *
 * <p>Order is stored as a CSV of keys in plain SharedPreferences — it's
 * not sensitive so we don't need EncryptedSharedPreferences.
 */
public final class DashboardOrderController {

    public static final String KEY_BALANCE      = "balance";
    public static final String KEY_PIE          = "pie";
    public static final String KEY_ACTIONS      = "actions";
    public static final String KEY_TRANSACTIONS = "transactions";

    private static final String PREFS_NAME = "dashboard_prefs";
    private static final String PREF_ORDER = "card_order";
    private static final String DEFAULT_ORDER =
            KEY_BALANCE + "," + KEY_PIE + "," + KEY_ACTIONS + "," + KEY_TRANSACTIONS;

    private DashboardOrderController() {}

    /** Returns the saved order as a list of keys, or the default order. */
    public static List<String> loadOrder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String csv = prefs.getString(PREF_ORDER, DEFAULT_ORDER);
        List<String> out = new ArrayList<>(Arrays.asList(csv.split(",")));
        // Defensive: if the stored list is missing a key (e.g. added later),
        // append it so nothing goes missing from the dashboard.
        for (String k : new String[]{ KEY_BALANCE, KEY_PIE, KEY_ACTIONS, KEY_TRANSACTIONS }) {
            if (!out.contains(k)) out.add(k);
        }
        return out;
    }

    /** Persists a new order. */
    public static void saveOrder(Context context, List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i));
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_ORDER, sb.toString()).apply();
    }

    /**
     * Re-orders the four sibling card views inside {@code container}
     * according to the saved order. Views are left untouched if the
     * container doesn't contain them (e.g. on a test layout).
     */
    public static void applyOrder(Context context, ViewGroup container,
                                  View balance, View pie,
                                  View actions, View transactions) {
        Map<String, View> viewsByKey = new LinkedHashMap<>();
        if (balance != null)      viewsByKey.put(KEY_BALANCE, balance);
        if (pie != null)          viewsByKey.put(KEY_PIE, pie);
        if (actions != null)      viewsByKey.put(KEY_ACTIONS, actions);
        if (transactions != null) viewsByKey.put(KEY_TRANSACTIONS, transactions);

        List<String> order = loadOrder(context);

        // Remove the four managed children and re-add them in the desired
        // order at the position where the first one currently sits. This
        // preserves any non-card siblings (toolbars, margins) above/below.
        int insertAt = -1;
        for (View v : viewsByKey.values()) {
            int idx = container.indexOfChild(v);
            if (idx >= 0 && (insertAt == -1 || idx < insertAt)) insertAt = idx;
        }
        if (insertAt < 0) return; // none present

        for (View v : viewsByKey.values()) {
            if (container.indexOfChild(v) >= 0) container.removeView(v);
        }
        for (String key : order) {
            View v = viewsByKey.get(key);
            if (v != null) {
                container.addView(v, insertAt++);
            }
        }
    }

    /** Human-readable label for the reorder UI. */
    public static int labelResFor(String key) {
        switch (key) {
            case KEY_BALANCE:      return R.string.dashboard_card_balance;
            case KEY_PIE:          return R.string.dashboard_card_pie;
            case KEY_ACTIONS:      return R.string.dashboard_card_actions;
            case KEY_TRANSACTIONS: return R.string.dashboard_card_transactions;
            default:               return R.string.dashboard_card_balance;
        }
    }
}

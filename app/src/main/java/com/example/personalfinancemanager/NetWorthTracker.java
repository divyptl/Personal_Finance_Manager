package com.example.personalfinancemanager;

import android.content.Context;

import java.util.List;

/**
 * Captures a point-in-time snapshot of the user's net worth and persists it
 * into the {@code net_worth_snapshot} table. Throttled to at most one
 * snapshot per {@link #MIN_GAP_MS} so invoking it on every MainActivity
 * launch doesn't spam the table — the chart only needs daily granularity.
 *
 * <p>All work runs on {@link AppDatabase#databaseWriteExecutor}; callers can
 * fire-and-forget from the UI thread.
 */
public final class NetWorthTracker {

    /** At most one snapshot every 6 hours — cheap and bounded. */
    private static final long MIN_GAP_MS = 6L * 60 * 60 * 1000;

    private NetWorthTracker() {}

    public static void maybeSnapshot(Context context) {
        final Context app = context.getApplicationContext();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(app);
                NetWorthSnapshotDao snapDao = db.netWorthSnapshotDao();

                NetWorthSnapshot latest = snapDao.getLatestSync();
                long now = System.currentTimeMillis();
                if (latest != null && (now - latest.getTimestamp()) < MIN_GAP_MS) {
                    return; // throttle
                }

                double invested = 0;
                List<Stock> stocks = db.stockDao().getAllStocksSync();
                if (stocks != null) {
                    for (Stock s : stocks) {
                        invested += s.getQuantity() * s.getAverageBuyPrice();
                    }
                }

                // Cash flow proxy = lifetime income − lifetime expenses. We use
                // the Transactions table as the source of truth because that's
                // what the rest of the app reasons about.
                List<Transaction> all = db.transactionDao().getAllTransactionsSync();
                double income = 0, expense = 0;
                if (all != null) {
                    for (Transaction t : all) {
                        String type = t.getType();
                        if (type == null) continue;
                        if (type.equalsIgnoreCase("income") || type.equalsIgnoreCase("Credit")) {
                            income += t.getAmount();
                        } else if (type.equalsIgnoreCase("expense") || type.equalsIgnoreCase("Debit")) {
                            expense += t.getAmount();
                        }
                    }
                }
                double cashFlow = income - expense;
                double netWorth = invested + cashFlow;

                snapDao.insert(new NetWorthSnapshot(now, invested, cashFlow, netWorth));
            } catch (Throwable t) {
                // Snapshotting must never crash the app or block the UI — silently
                // swallow (tracker is best-effort).
                android.util.Log.w("NetWorthTracker", "Snapshot failed", t);
            }
        });
    }
}

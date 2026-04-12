package com.example.personalfinancemanager;

import android.content.Context;

import java.util.Calendar;
import java.util.List;

/**
 * Checks current spending against budget limits and fires notifications.
 *
 * <p>Called from two places:
 * <ol>
 *   <li>{@link SmsReceiver} — immediately after a new expense is inserted,
 *       to fire a "budget exceeded" alert in real-time.</li>
 *   <li>{@link BudgetCheckWorker} — once daily via WorkManager, to fire
 *       "80 % warning" alerts proactively.</li>
 * </ol>
 *
 * <p>All database access is synchronous (runs on background threads only).
 */
public final class BudgetChecker {

    private BudgetChecker() { /* static utility */ }

    /**
     * Check a single category right after a transaction lands.
     * Fires a "budget exceeded" notification if the limit is breached.
     */
    public static void checkCategoryAfterInsert(Context context, String category) {
        AppDatabase db = AppDatabase.getDatabase(context);
        Budget budget = db.budgetDao().getBudgetForCategory(category);
        if (budget == null) return; // no limit set for this category

        long monthStart = startOfCurrentMonth();
        double spent = db.transactionDao().getCategoryExpenseSince(category, monthStart);

        if (spent >= budget.getMonthlyLimit()) {
            NotificationHelper.notifyBudgetExceeded(
                    context, category, spent, budget.getMonthlyLimit());
        }
    }

    /**
     * Check ALL categories. Fires "80 % warning" for approaching budgets
     * and "exceeded" for over-limit. Called daily by {@link BudgetCheckWorker}.
     */
    public static void checkAllBudgets(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        List<Budget> budgets = db.budgetDao().getAllBudgetsSync();
        if (budgets == null || budgets.isEmpty()) return;

        long monthStart = startOfCurrentMonth();

        for (Budget budget : budgets) {
            double spent = db.transactionDao()
                    .getCategoryExpenseSince(budget.getCategory(), monthStart);
            double limit = budget.getMonthlyLimit();
            if (limit <= 0) continue;

            double ratio = spent / limit;
            if (ratio >= 1.0) {
                NotificationHelper.notifyBudgetExceeded(
                        context, budget.getCategory(), spent, limit);
            } else if (ratio >= 0.8) {
                NotificationHelper.notifyBudgetWarning(
                        context, budget.getCategory(), spent, limit);
            }
        }
    }

    /** Returns epoch millis for midnight on the 1st of the current month. */
    static long startOfCurrentMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}

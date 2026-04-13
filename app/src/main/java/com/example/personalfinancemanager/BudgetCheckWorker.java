package com.example.personalfinancemanager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * A daily WorkManager job that scans every budget category and fires
 * push notifications for categories at 80 %+ or 100 %+ of their limit.
 *
 * <p>Scheduled in {@link WealthFlowApplication#onCreate()} with
 * {@code ExistingPeriodicWorkPolicy.KEEP} so that re-launching the app
 * never duplicates the job.
 */
public class BudgetCheckWorker extends Worker {

    private static final String TAG = "BudgetCheckWorker";
    private static final String UNIQUE_WORK_NAME = "daily_budget_check";

    public BudgetCheckWorker(@NonNull Context context,
                             @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            BudgetChecker.checkAllBudgets(getApplicationContext());
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "Budget check failed", t);
            return Result.retry();
        }
    }

    /**
     * Enqueue the periodic job. Safe to call on every app launch —
     * KEEP policy ensures it won't duplicate.
     */
    public static void enqueue(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BudgetCheckWorker.class,
                24, TimeUnit.HOURS       // repeat interval
        )
                .setInitialDelay(1, TimeUnit.HOURS)  // don't fire immediately on install
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }
}

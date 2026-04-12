package com.example.personalfinancemanager;

import android.app.Application;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/**
 * Application entry point. Owns one-time initialization (PDFBox, ServiceLocator)
 * and acts as the root from which dependencies are resolved.
 *
 * <p>This replaces the previous pattern of activities and receivers
 * directly instantiating their own collaborators ({@code new TransactionRepository(...)},
 * {@code new AngelOneHelper(...)}). All collaborators are now obtained from
 * {@link ServiceLocator}, which makes them mockable for tests and prevents
 * accidental duplication of state (e.g. the previous static JWT in
 * {@code AngelOneHelper}).
 */
public class WealthFlowApplication extends Application {

    private static final String TAG = "WealthFlowApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // PDFBox needs its asset bundle initialized before any parsing call.
        try {
            PDFBoxResourceLoader.init(getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "PDFBox init failed", t);
        }

        // Eagerly initialize the ServiceLocator so that broadcast receivers
        // (which can fire before any activity has touched the locator) get a
        // ready-to-use instance.
        ServiceLocator.initialize(this);

        // Create notification channels (idempotent — safe to call every launch).
        NotificationHelper.createChannels(this);

        // Schedule the daily budget check worker (idempotent — ExistingPeriodicWorkPolicy.KEEP
        // ensures it doesn't duplicate if already enqueued).
        BudgetCheckWorker.enqueue(this);
    }
}

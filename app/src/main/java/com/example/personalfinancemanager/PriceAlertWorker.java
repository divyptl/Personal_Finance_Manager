package com.example.personalfinancemanager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Daily WorkManager job that fetches the latest traded price for every
 * ticker covered by an enabled {@link PriceAlert} and fires a push
 * notification when the price crosses a configured bound.
 *
 * <p>Each alert has a 24 h cooldown — after firing once we stamp
 * {@code lastNotifiedAt} and the worker skips the alert until the cooldown
 * has elapsed, so a stock parked just above the threshold doesn't generate
 * a steady stream of duplicates.
 *
 * <p>Fail-open semantics: broker auth errors, network errors, and empty
 * price maps all return {@link Result#success()} (with logging) rather
 * than retry-storming. The next scheduled run will try again.
 */
public class PriceAlertWorker extends Worker {

    private static final String TAG = "PriceAlertWorker";
    private static final String UNIQUE_WORK_NAME = "daily_price_alert_check";
    private static final long COOLDOWN_MS = 24L * 60L * 60L * 1000L;
    /** Cap on how long we block waiting for the broker's async callback. */
    private static final long FETCH_TIMEOUT_SECONDS = 30L;

    public PriceAlertWorker(@NonNull Context context,
                            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context ctx = getApplicationContext();
            AppDatabase db = AppDatabase.getDatabase(ctx);

            List<PriceAlert> alerts = db.priceAlertDao().getAllEnabledSync();
            if (alerts.isEmpty()) {
                return Result.success();
            }

            // Build the Stock list we hand to BrokerApi. We need the
            // symbolToken for tickers already held in the portfolio —
            // for un-held tickers the broker lookup will fall back to the
            // ticker string (AngelOneBrokerApi tolerates missing tokens
            // and simply returns no price, which we treat as a no-op).
            Map<String, Stock> byTicker = new HashMap<>();
            for (Stock s : db.stockDao().getAllStocksSync()) {
                byTicker.put(s.getTicker(), s);
            }

            List<Stock> probe = new ArrayList<>();
            for (PriceAlert a : alerts) {
                Stock held = byTicker.get(a.getTicker());
                if (held != null) {
                    probe.add(held);
                } else {
                    // Synthetic probe row — no token, but the ticker is
                    // enough for brokers that resolve by symbol.
                    probe.add(new Stock(a.getTicker(), null, 0, 0, null));
                }
            }

            BrokerApi broker = ServiceLocator.get(ctx).brokerApi();
            if (!broker.isAuthenticated()) {
                Log.i(TAG, "Broker not authenticated — skipping alert check");
                return Result.success();
            }

            Map<String, Double> prices = fetchBlocking(broker, probe);
            if (prices == null || prices.isEmpty()) {
                Log.i(TAG, "No prices returned — skipping alert check");
                return Result.success();
            }

            long now = System.currentTimeMillis();
            for (PriceAlert a : alerts) {
                if (now - a.getLastNotifiedAt() < COOLDOWN_MS) continue;

                Double ltp = prices.get(a.getTicker());
                if (ltp == null) continue;

                Double lo = a.getLowerBound();
                Double hi = a.getUpperBound();

                if (hi != null && ltp >= hi) {
                    NotificationHelper.notifyPriceAlert(ctx, a.getTicker(),
                            ltp, hi, NotificationHelper.Direction.ABOVE);
                    db.priceAlertDao().stampNotified(a.getId(), now);
                } else if (lo != null && ltp <= lo) {
                    NotificationHelper.notifyPriceAlert(ctx, a.getTicker(),
                            ltp, lo, NotificationHelper.Direction.BELOW);
                    db.priceAlertDao().stampNotified(a.getId(), now);
                }
            }

            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "Price alert check failed", t);
            return Result.retry();
        }
    }

    /**
     * Bridges the async {@link BrokerApi#fetchBatchLtp} into the synchronous
     * Worker thread via a CountDownLatch. The broker posts its callback on
     * the main thread, which is fine because the Worker runs on a background
     * executor.
     */
    @NonNull
    private static Map<String, Double> fetchBlocking(BrokerApi broker, List<Stock> stocks) {
        final Map<String, Double>[] result = new Map[]{ Collections.<String, Double>emptyMap() };
        final CountDownLatch latch = new CountDownLatch(1);
        broker.fetchBatchLtp(stocks, priceMap -> {
            if (priceMap != null) result[0] = priceMap;
            latch.countDown();
        });
        try {
            if (!latch.await(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.w(TAG, "Broker fetch timed out after " + FETCH_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /** Enqueue the periodic job. Safe to call on every app launch. */
    public static void enqueue(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PriceAlertWorker.class,
                24, TimeUnit.HOURS
        )
                .setInitialDelay(2, TimeUnit.HOURS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }
}

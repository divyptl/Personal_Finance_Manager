package com.example.personalfinancemanager;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * Manages notification channels and fires budget-alert notifications.
 *
 * <p>Two channel types:
 * <ul>
 *   <li><b>budget_exceeded</b> — HIGH importance, fires when spending hits 100 %+ of limit</li>
 *   <li><b>budget_warning</b>  — DEFAULT importance, fires at 80 % of limit (daily check)</li>
 * </ul>
 */
public final class NotificationHelper {

    private static final String CHANNEL_EXCEEDED = "budget_exceeded";
    private static final String CHANNEL_WARNING  = "budget_warning";

    /** Must be called once from {@link WealthFlowApplication#onCreate()}. */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel exceeded = new NotificationChannel(
                CHANNEL_EXCEEDED,
                "Budget Exceeded",
                NotificationManager.IMPORTANCE_HIGH
        );
        exceeded.setDescription("Alerts when you exceed a category spending limit.");

        NotificationChannel warning = new NotificationChannel(
                CHANNEL_WARNING,
                "Budget Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        warning.setDescription("Daily warnings when you approach a category spending limit.");

        nm.createNotificationChannel(exceeded);
        nm.createNotificationChannel(warning);
    }

    /**
     * Fires a "Budget Exceeded" notification.
     *
     * @param category  the expense category that was exceeded
     * @param spent     total amount spent so far this month
     * @param limit     the monthly limit for this category
     */
    public static void notifyBudgetExceeded(Context context, String category,
                                            double spent, double limit) {
        if (!canPost(context)) return;

        String title = String.format(Locale.getDefault(),
                "%s budget exceeded!", category);
        String body = String.format(Locale.getDefault(),
                "You've spent \u20B9%.0f of your \u20B9%.0f %s budget this month.",
                spent, limit, category);

        int notifId = ("exceeded_" + category).hashCode();
        post(context, CHANNEL_EXCEEDED, notifId, title, body,
                NotificationCompat.PRIORITY_HIGH);
    }

    /**
     * Fires an "80 % warning" notification.
     */
    public static void notifyBudgetWarning(Context context, String category,
                                           double spent, double limit) {
        if (!canPost(context)) return;

        int percent = (int) ((spent / limit) * 100);
        String title = String.format(Locale.getDefault(),
                "%s budget: %d%% used", category, percent);
        String body = String.format(Locale.getDefault(),
                "You've spent \u20B9%.0f of your \u20B9%.0f %s budget. Consider slowing down.",
                spent, limit, category);

        int notifId = ("warning_" + category).hashCode();
        post(context, CHANNEL_WARNING, notifId, title, body,
                NotificationCompat.PRIORITY_DEFAULT);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // pre-13 → always allowed
    }

    private static void post(Context context, String channelId, int notifId,
                             String title, String body, int priority) {
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, notifId, tapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(pi);

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted — silent no-op
        }
    }
}

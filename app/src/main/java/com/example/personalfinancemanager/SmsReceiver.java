package com.example.personalfinancemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Thin glue between the system's SMS_RECEIVED broadcast and our parser.
 *
 * <p>All parsing logic now lives in {@link SmsTransactionParser}, which is a
 * pure Java class with no Android dependencies — that lets us unit-test the
 * categorization rules in milliseconds without Robolectric.
 *
 * <p>Collaborators (parser, repository) are obtained from {@link ServiceLocator}
 * instead of being constructed inside {@code onReceive}, which previously
 * created a new database connection for every single SMS.
 *
 * <p>Spoofing protection: this receiver is exported with
 * {@code android:permission="android.permission.BROADCAST_SMS"} in the
 * manifest, so only the OS can deliver to it.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    // Dedup: many banks send the same debit/credit alert from multiple
    // senders (e.g. HDFCBK + VM-HDFCBK) or the OS re-delivers on roaming.
    // We key on (amount + type + last-4-of-account-or-hash) within a 5-min
    // window. Pure in-memory: a cold boot re-accepts — acceptable tradeoff
    // vs. adding another Room table for this.
    private static final long DEDUP_WINDOW_MS = 5 * 60 * 1000L;
    private static final java.util.LinkedHashMap<String, Long> sRecentKeys =
            new java.util.LinkedHashMap<String, Long>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> e) {
                    return size() > 64;
                }
            };

    private static synchronized boolean isDuplicate(Transaction txn) {
        String key = txn.getType() + "|" + String.format(java.util.Locale.ROOT,
                "%.2f", txn.getAmount()) + "|" + fingerprint(txn.getMessage());
        long now = System.currentTimeMillis();
        Long seenAt = sRecentKeys.get(key);
        if (seenAt != null && (now - seenAt) < DEDUP_WINDOW_MS) {
            return true;
        }
        sRecentKeys.put(key, now);
        return false;
    }

    /** Extract the last 4 digits of any account-like token, else hash-of-body. */
    private static String fingerprint(String body) {
        if (body == null) return "0";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:a/c|ac|acct|card)[^0-9]{0,6}(\\d{4,})")
                .matcher(body);
        if (m.find()) {
            String digits = m.group(1);
            return digits.substring(Math.max(0, digits.length() - 4));
        }
        return Integer.toHexString(body.hashCode());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        ServiceLocator services = ServiceLocator.get(context);
        SmsTransactionParser parser = services.smsParser();
        TransactionRepository repository = services.transactionRepository();
        String format = bundle.getString("format");

        for (Object pdu : pdus) {
            try {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (sms == null) continue;

                Transaction txn = parser.parse(
                        sms.getDisplayOriginatingAddress(),
                        sms.getMessageBody()
                );
                if (txn != null) {
                    if (isDuplicate(txn)) {
                        Log.d(TAG, "Skipped duplicate SMS within dedup window");
                        continue;
                    }
                    repository.insert(txn);
                    // Check budget limit in real-time after every expense
                    if ("expense".equals(txn.getType()) && txn.getCategory() != null) {
                        try {
                            BudgetChecker.checkCategoryAfterInsert(
                                    context, txn.getCategory());
                        } catch (Throwable t) {
                            Log.w(TAG, "Budget check failed", t);
                        }
                    }
                }
            } catch (Throwable t) {
                // Never let a malformed SMS crash the receiver — it would be
                // counted as an ANR by the system.
                Log.e(TAG, "Failed to process SMS PDU", t);
            }
        }
    }
}

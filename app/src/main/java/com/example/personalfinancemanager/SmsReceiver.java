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

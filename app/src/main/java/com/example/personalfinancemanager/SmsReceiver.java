package com.example.personalfinancemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    // Add this right before the final closing brace '}' of the SmsReceiver class
    private double extractAmount(String message) {
        // Looks for "Rs.", "INR", or "₹" followed by numbers and decimals
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            try {
                // Remove commas (e.g., 1,500.00 becomes 1500.00) so Java can parse it
                String amountStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(amountStr);
            } catch (Exception e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                for (Object pdu : pdus) {
                    // MODERN WAY
                    String format = bundle.getString("format");
                    android.telephony.SmsMessage sms = android.telephony.SmsMessage.createFromPdu((byte[]) pdu, format);

                    String sender = sms.getDisplayOriginatingAddress();

                    // FEATURE 2: SECURITY LAYER (Ignore regular 10-digit mobile numbers)
                    if (sender != null && sender.matches(".*\\+?\\d{10}.*")) {
                        continue; // Skip this SMS, it's not from a bank header (like AD-HDFCBK)
                    }

                    String messageBody = sms.getMessageBody().toLowerCase();

                    if (messageBody.contains("debited") || messageBody.contains("sent") || messageBody.contains("spent")) {
                        double amount = extractAmount(messageBody);
                        if (amount > 0) {

                            // FEATURE 3: CATEGORIZATION HEURISTICS
                            String category = "Other";
                            if (messageBody.contains("zomato") || messageBody.contains("swiggy") || messageBody.contains("mcdonalds")) {
                                category = "Food & Dining";
                            } else if (messageBody.contains("uber") || messageBody.contains("ola") || messageBody.contains("irctc")) {
                                category = "Transport";
                            } else if (messageBody.contains("amazon") || messageBody.contains("flipkart") || messageBody.contains("blinkit")) {
                                category = "Shopping";
                            } else if (messageBody.contains("jio") || messageBody.contains("airtel") || messageBody.contains("bescom")) {
                                category = "Bills & Utilities";
                            }

                            Transaction transaction = new Transaction(messageBody, amount, System.currentTimeMillis(), "expense", category);

                            TransactionRepository repository = new TransactionRepository((android.app.Application) context.getApplicationContext());
                            repository.insert(transaction);
                        }
                    }
                }
            }
        }
    }
}
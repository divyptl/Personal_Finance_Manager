package com.example.personalfinancemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {

    // Amount patterns: Rs.1234, Rs 1,234.56, INR 500, ₹750.00, Rs1234
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:rs\\.?|inr|\\u20B9)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    // --- DEBIT KEYWORDS (all major Indian banks) ---
    private static final String[] DEBIT_KEYWORDS = {
            "debited", "deducted", "withdrawn", "spent", "paid",
            "sent", "transferred", "purchase", "payment of",
            "debit", "dr", "charged", "used at", "txn of",
            "deducted from", "transaction on", "money sent"
    };

    // --- CREDIT KEYWORDS ---
    private static final String[] CREDIT_KEYWORDS = {
            "credited", "received", "deposited", "refund",
            "cashback", "reversed", "credit", "cr",
            "money received", "added to", "settled"
    };

    // --- IGNORE KEYWORDS (promotional / OTP / non-transactional) ---
    private static final String[] IGNORE_KEYWORDS = {
            "otp", "one time password", "verification code",
            "offer", "apply now", "pre-approved", "limit increased",
            "emi", "due date", "minimum due", "bill generated",
            "get up to", "cashback up to", "win", "congrat",
            "download", "update", "install", "click here",
            "upgrade", "activate", "expir"
    };

    private double extractAmount(String message) {
        Matcher matcher = AMOUNT_PATTERN.matcher(message);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private boolean isBankSender(String sender) {
        if (sender == null) return false;
        // Bank SMS comes from short codes or alphanumeric IDs like:
        // AD-HDFCBK, VM-SBIINB, BZ-ICICIB, etc.
        // Regular 10-digit mobile numbers are NOT banks
        if (sender.replaceAll("[^0-9]", "").length() >= 10) return false;
        return true;
    }

    private String categorize(String message) {
        // --- Food & Dining ---
        if (matchesAny(message, "zomato", "swiggy", "mcdonalds", "mcdonald",
                "dominos", "domino", "pizza hut", "pizzahut", "kfc",
                "burger king", "starbucks", "cafe coffee", "ccd",
                "subway", "restaurant", "dining", "food",
                "biryani", "chicken", "bakery", "haldiram",
                "baskin", "dunkin", "chaayos", "freshmenu",
                "eatsure", "faasos", "behrouz", "box8")) {
            return "Food & Dining";
        }

        // --- Transport ---
        if (matchesAny(message, "uber", "ola", "rapido", "irctc",
                "railway", "metro", "bus", "fuel", "petrol",
                "diesel", "indian oil", "iocl", "bpcl", "hpcl",
                "hp petrol", "bharat petroleum", "shell",
                "nhai", "fastag", "toll", "parking",
                "yulu", "bounce", "vogo", "bluedart", "delhivery")) {
            return "Transport";
        }

        // --- Shopping ---
        if (matchesAny(message, "amazon", "flipkart", "myntra", "ajio",
                "meesho", "snapdeal", "nykaa", "tata cliq",
                "blinkit", "zepto", "bigbasket", "dmart",
                "reliance", "croma", "vijay sales",
                "shoppers stop", "lifestyle", "westside",
                "decathlon", "ikea", "pepperfry", "urbanladder",
                "firstcry", "lenskart", "boat", "noise",
                "mall", "mart", "store", "shop")) {
            return "Shopping";
        }

        // --- Groceries ---
        if (matchesAny(message, "grocery", "grofers", "jiomart",
                "swiggy instamart", "dunzo", "milkbasket",
                "nature basket", "spencers", "more supermarket",
                "spar", "star bazaar", "easyday")) {
            return "Groceries";
        }

        // --- Bills & Utilities ---
        if (matchesAny(message, "jio", "airtel", "vodafone", "vi ",
                "bsnl", "bescom", "electricity", "water bill",
                "gas bill", "broadband", "wifi", "internet",
                "tata power", "adani", "torrent power",
                "mahanagar gas", "indane", "lic ", "insurance",
                "premium", "renewal", "recharge", "postpaid",
                "prepaid", "dth", "tata sky", "dish tv",
                "airtel xstream", "act fibernet")) {
            return "Bills & Utilities";
        }

        // --- Health ---
        if (matchesAny(message, "pharma", "pharmacy", "medical",
                "hospital", "doctor", "dr.", "apollo",
                "medplus", "1mg", "netmeds", "pharmeasy",
                "tata 1mg", "clinic", "diagnostic",
                "pathology", "health", "dental", "eye care")) {
            return "Health";
        }

        // --- Entertainment ---
        if (matchesAny(message, "netflix", "hotstar", "disney",
                "spotify", "prime video", "youtube", "gaming",
                "movie", "pvr", "inox", "bookmyshow",
                "gaana", "wynk", "zee5", "sonyliv",
                "jiocinema", "voot", "mxplayer")) {
            return "Entertainment";
        }

        // --- Education ---
        if (matchesAny(message, "school", "college", "university",
                "tuition", "udemy", "coursera", "unacademy",
                "byju", "vedantu", "upgrad", "simplilearn",
                "course", "exam", "education", "fees")) {
            return "Education";
        }

        // --- Rent & Housing ---
        if (matchesAny(message, "rent", "maintenance", "society",
                "housing", "landlord", "nobroker", "magicbricks",
                "flat", "apartment", "pg ")) {
            return "Rent & Housing";
        }

        // --- Investments ---
        if (matchesAny(message, "zerodha", "groww", "upstox",
                "angelone", "angel one", "mutual fund", "sip",
                "investment", "mf purchase", "nps", "ppf",
                "smallcase", "kuvera", "coin", "paytm money",
                "ipo", "trading")) {
            return "Investments";
        }

        // --- UPI / Transfer (generic person-to-person) ---
        if (matchesAny(message, "upi", "neft", "rtgs", "imps",
                "transfer", "sent to", "paid to", "p2p")) {
            return "Transfer";
        }

        return "Other";
    }

    private boolean matchesAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) return true;
        }
        return false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        for (Object pdu : pdus) {
            String format = bundle.getString("format");
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);

            String sender = sms.getDisplayOriginatingAddress();

            // Only process SMS from bank/service senders (not regular phone numbers)
            if (!isBankSender(sender)) continue;

            String messageBody = sms.getMessageBody();
            String lowerMessage = messageBody.toLowerCase();

            // Skip promotional / OTP / non-transactional messages
            if (containsAny(lowerMessage, IGNORE_KEYWORDS)) continue;

            double amount = extractAmount(lowerMessage);
            if (amount <= 0) continue;

            String type;
            if (containsAny(lowerMessage, DEBIT_KEYWORDS)) {
                type = "expense";
            } else if (containsAny(lowerMessage, CREDIT_KEYWORDS)) {
                type = "income";
            } else {
                continue; // Not a transaction SMS
            }

            String category = categorize(lowerMessage);

            // Use original message (not lowercased) for display
            Transaction transaction = new Transaction(
                    messageBody, amount, System.currentTimeMillis(), type, category);

            TransactionRepository repository = new TransactionRepository(
                    (android.app.Application) context.getApplicationContext());
            repository.insert(transaction);
        }
    }
}

package com.example.personalfinancemanager;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java parser that turns a single SMS message into a {@link Transaction}
 * (or {@code null} if the message isn't a bank transaction notification).
 *
 * <p>This used to live inside {@link SmsReceiver}, intermixed with
 * {@code BroadcastReceiver} lifecycle code, which made it impossible to test
 * without spinning up the Android framework. Extracted as a plain Java class
 * with no Android dependencies whatsoever, the parsing logic is exercised by
 * {@code SmsTransactionParserTest} on the host JVM in milliseconds.
 *
 * <p>Behavioural contract (asserted by the unit tests):
 *   <ul>
 *     <li>Returns null for messages from non-bank senders (regular phone numbers).</li>
 *     <li>Returns null for OTP / promotional / EMI / bill-due notifications.</li>
 *     <li>Returns null when no amount can be extracted.</li>
 *     <li>Classifies as "expense" / "income" based on debit/credit keywords.</li>
 *     <li>Returns null when neither debit nor credit keywords match (so we
 *         don't incorrectly count "balance enquiry" SMS).</li>
 *     <li>Categorizes against an exhaustive merchant keyword list.</li>
 *   </ul>
 */
public class SmsTransactionParser {

    // Amount patterns: Rs.1234, Rs 1,234.56, INR 500, ₹750.00, Rs1234
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:rs\\.?|inr|\\u20B9)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    private static final String[] DEBIT_KEYWORDS = {
            "debited", "deducted", "withdrawn", "spent", "paid",
            "sent", "transferred", "purchase", "payment of",
            "debit", "dr", "charged", "used at", "txn of",
            "deducted from", "transaction on", "money sent"
    };

    private static final String[] CREDIT_KEYWORDS = {
            "credited", "received", "deposited", "refund",
            "cashback", "reversed", "credit", "cr",
            "money received", "added to", "settled"
    };

    private static final String[] IGNORE_KEYWORDS = {
            "otp", "one time password", "verification code",
            "offer", "apply now", "pre-approved", "limit increased",
            "emi", "due date", "minimum due", "bill generated",
            "get up to", "cashback up to", "win", "congrat",
            "download", "update", "install", "click here",
            "upgrade", "activate", "expir"
    };

    /**
     * @return a {@link Transaction} ready to insert, or null if the SMS
     *         should be ignored.
     */
    @Nullable
    public Transaction parse(String sender, String messageBody) {
        if (messageBody == null) return null;
        if (!isBankSender(sender)) return null;

        String lower = messageBody.toLowerCase();

        if (containsAny(lower, IGNORE_KEYWORDS)) return null;

        double amount = extractAmount(lower);
        if (amount <= 0) return null;

        String type;
        if (containsAny(lower, DEBIT_KEYWORDS)) {
            type = "expense";
        } else if (containsAny(lower, CREDIT_KEYWORDS)) {
            type = "income";
        } else {
            return null;
        }

        String category = categorize(lower);
        return new Transaction(messageBody, amount, System.currentTimeMillis(), type, category);
    }

    /** Visible for testing. */
    boolean isBankSender(String sender) {
        if (sender == null) return false;
        // Bank SMS comes from short codes or alphanumeric IDs (e.g. AD-HDFCBK,
        // VM-SBIINB). Regular 10-digit mobile numbers are NEVER banks.
        return sender.replaceAll("[^0-9]", "").length() < 10;
    }

    /** Visible for testing. */
    double extractAmount(String message) {
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

    /** Visible for testing. */
    String categorize(String message) {
        if (matchesAny(message, "zomato", "swiggy", "mcdonalds", "mcdonald",
                "dominos", "domino", "pizza hut", "pizzahut", "kfc",
                "burger king", "starbucks", "cafe coffee", "ccd",
                "subway", "restaurant", "dining", "food",
                "biryani", "chicken", "bakery", "haldiram",
                "baskin", "dunkin", "chaayos", "freshmenu",
                "eatsure", "faasos", "behrouz", "box8")) {
            return "Food & Dining";
        }
        if (matchesAny(message, "uber", "ola", "rapido", "irctc",
                "railway", "metro", "bus", "fuel", "petrol",
                "diesel", "indian oil", "iocl", "bpcl", "hpcl",
                "hp petrol", "bharat petroleum", "shell",
                "nhai", "fastag", "toll", "parking",
                "yulu", "bounce", "vogo", "bluedart", "delhivery")) {
            return "Transport";
        }
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
        if (matchesAny(message, "grocery", "grofers", "jiomart",
                "swiggy instamart", "dunzo", "milkbasket",
                "nature basket", "spencers", "more supermarket",
                "spar", "star bazaar", "easyday")) {
            return "Groceries";
        }
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
        if (matchesAny(message, "pharma", "pharmacy", "medical",
                "hospital", "doctor", "dr.", "apollo",
                "medplus", "1mg", "netmeds", "pharmeasy",
                "tata 1mg", "clinic", "diagnostic",
                "pathology", "health", "dental", "eye care")) {
            return "Health";
        }
        if (matchesAny(message, "netflix", "hotstar", "disney",
                "spotify", "prime video", "youtube", "gaming",
                "movie", "pvr", "inox", "bookmyshow",
                "gaana", "wynk", "zee5", "sonyliv",
                "jiocinema", "voot", "mxplayer")) {
            return "Entertainment";
        }
        if (matchesAny(message, "school", "college", "university",
                "tuition", "udemy", "coursera", "unacademy",
                "byju", "vedantu", "upgrad", "simplilearn",
                "course", "exam", "education", "fees")) {
            return "Education";
        }
        if (matchesAny(message, "rent", "maintenance", "society",
                "housing", "landlord", "nobroker", "magicbricks",
                "flat", "apartment", "pg ")) {
            return "Rent & Housing";
        }
        if (matchesAny(message, "zerodha", "groww", "upstox",
                "angelone", "angel one", "mutual fund", "sip",
                "investment", "mf purchase", "nps", "ppf",
                "smallcase", "kuvera", "coin", "paytm money",
                "ipo", "trading")) {
            return "Investments";
        }
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
}

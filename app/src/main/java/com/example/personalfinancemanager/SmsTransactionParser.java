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

    // ---------- Amount patterns ----------
    //
    // We run three passes in order of confidence. The first pass that yields
    // a sensible number wins. This ordering matters: the currency-prefixed
    // pass is unambiguous and should always take precedence over heuristics.

    /** Pass 1: explicit currency prefix — "Rs.1234", "INR 500", "\u20B9750.00", "Rs1234". */
    private static final Pattern AMOUNT_WITH_CURRENCY_PREFIX = Pattern.compile(
            "(?:rs\\.?|inr|\\u20B9)\\s*([0-9,]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    /** Pass 2: amount immediately following an action verb ("debited by 5.00",
     *  "credited with 1,000", "sent 500 to ..."). The verb anchors the
     *  extraction so we never pick up phone numbers, reference numbers, OTPs,
     *  account suffixes or dates. The optional connector word ("by / with /
     *  for / of") and optional currency token cover the format variants we've
     *  seen from SBI, HDFC, ICICI, Axis, Kotak, PNB and BoB. */
    private static final Pattern AMOUNT_AFTER_VERB = Pattern.compile(
            "(?:debited|credited|withdrawn|withdrawal|deposited|received|sent|spent|paid"
                    + "|deducted|charged|transferred|purchase(?:d)?|txn\\s+of"
                    + "|payment\\s+of|transaction\\s+of|amount\\s+of)"
                    + "(?:\\s+(?:by|with|for|of|from|to|amount))?"
                    + "\\s*(?:rs\\.?|inr|\\u20B9)?\\s*"
                    + "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    /** Pass 3: suffixed currency — "500 INR spent", "1,234.56 Rs". Rare but
     *  seen on a few older PSU bank templates. */
    private static final Pattern AMOUNT_WITH_CURRENCY_SUFFIX = Pattern.compile(
            "([0-9,]+(?:\\.[0-9]+)?)\\s*(?:rs\\.?|inr|\\u20B9)",
            Pattern.CASE_INSENSITIVE);

    private static final String[] DEBIT_KEYWORDS = {
            "debited", "deducted", "withdrawn", "withdrawal", "spent", "paid",
            "sent", "transferred", "purchase", "payment of",
            "debit", "dr", "charged", "used at", "txn of",
            "deducted from", "transaction on", "money sent",
            "trf to", "transfer to", "sent to", "paid to",
            "withdrawn from", "debit alert", "purchased for",
            "transaction of", "atm withdrawal", "paying"
    };

    private static final String[] CREDIT_KEYWORDS = {
            "credited", "received", "deposited", "refund",
            "reversed", "credit", "cr",
            "money received", "added to", "settled",
            "credit alert", "received from", "credited to",
            "has been credited", "amount credited"
            // Note: "cashback" deliberately removed. Real cashback messages
            // ("Rs.50 cashback credited to your account") still trigger via
            // "credited". Standalone "cashback" matches promo SMS like
            // "unlock Rs.50 cashback every month".
    };

    private static final String[] IGNORE_KEYWORDS = {
            // OTP / verification
            "otp", "one time password", "verification code", "verify your",
            "secure code", "auth code",

            // Account-opening / sales pitches
            "open a bank", "open an account", "open ur account",
            "open your account", "open a/c", "opening a", "open free",
            "new bank account", "instant account",

            // Generic marketing verbs / phrases
            "offer", "apply now", "apply today", "apply for",
            "pre-approved", "preapproved", "limit increased",
            "get up to", "get upto", "earn up to", "earn upto",
            "save up to", "save upto", "win up to", "win upto",
            "win rs", "upto rs", "up to rs",
            "cashback up to", "cashback upto", "cashback every",
            "monthly cashback", "guaranteed cashback",
            "unlock rs", "unlock cashback", "exclusive",
            "limited offer", "limited time", "special offer",
            "festive offer", "introducing", "announcing", "presenting",
            "rewards worth", "points worth", "complimentary",
            "free for", "lifetime free", "no charges", "zero charges",
            "0% interest", "zero processing",

            // Loan / card pitches
            "personal loan", "home loan", "gold loan", "car loan",
            "instant loan", "loan offer", "loan upto", "loan up to",
            "loan at", "loan against", "credit card offer",
            "card offer", "free card", "new card", "interest at",
            "lowest interest", "low interest",

            // EMI / bill due reminders (not new transactions)
            "emi", "due date", "minimum due", "bill generated",
            "payment due", "is due on", "overdue",

            // CTAs
            "click here", "click to", "tap to", "visit ", "log on to",
            "log in to", "register at", "register on", "register your",
            "missed call", "give miss call", "give a missed call",
            "to know more", "for details call", "to apply", "sms to",

            // KYC prompts (account-action, not txn)
            "kyc pending", "complete kyc", "update kyc", "kyc update",
            "re-kyc", "re kyc",

            // App / install nudges
            "download", "install", "upgrade", "activate", "expir",

            // Failed / declined txns (NOT new money movements)
            "transaction failed", "txn failed", "transaction declined",
            "txn declined", "could not be processed", "unsuccessful",

            // Misc promo
            "win", "congrat", "lucky", "winner", "scratch card",
            "convert to emi", "convert into emi"
    };

    /**
     * Hard requirement: a real bank-transaction SMS always references the
     * underlying instrument — an account, a card, a UPI/IMPS/NEFT/RTGS rail,
     * an ATM, a wallet, or carries a balance / reference number. Promotional
     * SMS almost never contain any of these, so requiring at least one anchor
     * eliminates the entire class of false positives like
     * "Open a bank account today & unlock Rs.50 cashback every month".
     *
     * <p>Importantly, the loose word "account" is NOT an anchor — promo
     * messages routinely say "Open an account" / "your savings account" /
     * "premium account". Only the abbreviated forms (a/c, acct, ac no) and
     * the structured rails count.
     */
    private static final String[] TRANSACTIONAL_ANCHORS = {
            "a/c", "acct", "ac no", "ac.no", "ac:",
            "credit card", "debit card", "card xx", "card x", "card no",
            "card ending", "card *", "your card",
            "upi", "imps", "neft", "rtgs", "vpa", "p2p", "p2m",
            "atm", "wallet",
            "ref no", "refno", "ref:", "ref.", "txn id", "txn no", "txn:",
            "transaction id", "rrn",
            "avl bal", "avbl bal", "available bal", "ledger bal",
            "available balance", "current balance"
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

        // Anchor gate: reject SMS that don't reference any real banking
        // instrument (account, card, UPI rail, wallet, ref no, balance...).
        // This is the primary defence against promotional false positives.
        if (!containsAny(lower, TRANSACTIONAL_ANCHORS)) return null;

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
        // Pass 1 — explicit currency prefix wins.
        double v = firstMatch(AMOUNT_WITH_CURRENCY_PREFIX, message);
        if (v > 0) return v;
        // Pass 2 — amount anchored to an action verb.
        v = firstMatch(AMOUNT_AFTER_VERB, message);
        if (v > 0) return v;
        // Pass 3 — suffix form.
        v = firstMatch(AMOUNT_WITH_CURRENCY_SUFFIX, message);
        return v;
    }

    private static double firstMatch(Pattern p, String message) {
        Matcher matcher = p.matcher(message);
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
                "ipo", "trading", "fyers", "5paisa", "icicidirect",
                "hdfc securities", "kotak securities", "motilal",
                "sharekhan", "edelweiss", "demat")) {
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

package com.example.personalfinancemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link SmsTransactionParser}.
 *
 * <p>These run on the host JVM (no Robolectric, no instrumentation) because
 * the parser was deliberately extracted as a pure-Java class with zero
 * Android dependencies. The full suite finishes in well under a second.
 *
 * <p>Coverage targets:
 *   <ul>
 *     <li>Sender filtering (banks vs personal numbers)</li>
 *     <li>Amount extraction across the Rs / Rs. / INR / ₹ formats</li>
 *     <li>Debit vs credit classification</li>
 *     <li>Ignore-list (OTP, promotional, EMI bills)</li>
 *     <li>Category routing for the major merchant verticals</li>
 *   </ul>
 */
public class SmsTransactionParserTest {

    private SmsTransactionParser parser;

    @Before
    public void setUp() {
        parser = new SmsTransactionParser();
    }

    // ---------- Sender filtering ----------

    @Test
    public void rejects_message_from_personal_phone_number() {
        Transaction t = parser.parse("9876543210",
                "Rs.500 debited from your A/c at Zomato");
        assertNull("Personal numbers must never produce a transaction", t);
    }

    @Test
    public void accepts_message_from_alphanumeric_bank_id() {
        Transaction t = parser.parse("AD-HDFCBK",
                "Rs.500 debited from your A/c at Zomato");
        assertNotNull(t);
    }

    @Test
    public void accepts_message_from_short_code() {
        Transaction t = parser.parse("VK-SBI",
                "INR 1200 spent on your card at Amazon");
        assertNotNull(t);
    }

    @Test
    public void rejects_null_sender() {
        assertNull(parser.parse(null, "Rs.500 debited"));
    }

    // ---------- Amount extraction ----------

    @Test
    public void extracts_rs_dot_format() {
        assertEquals(500.0, parser.extractAmount("rs.500 debited"), 0.001);
    }

    @Test
    public void extracts_rs_with_space() {
        assertEquals(1234.56, parser.extractAmount("rs 1,234.56 debited"), 0.001);
    }

    @Test
    public void extracts_inr_format() {
        assertEquals(750.0, parser.extractAmount("inr 750 spent"), 0.001);
    }

    @Test
    public void extracts_rupee_symbol() {
        assertEquals(2500.0, parser.extractAmount("\u20b92,500 paid to swiggy"), 0.001);
    }

    @Test
    public void returns_zero_when_no_amount() {
        assertEquals(0.0, parser.extractAmount("balance enquiry"), 0.001);
    }

    @Test
    public void handles_amount_without_separator() {
        assertEquals(1234.0, parser.extractAmount("rs1234 debited"), 0.001);
    }

    // ---------- Ignore list ----------

    @Test
    public void ignores_otp_messages() {
        assertNull(parser.parse("VM-HDFCBK",
                "Your OTP is 123456. Do not share. Rs.500 transaction pending."));
    }

    @Test
    public void ignores_promotional_offers() {
        assertNull(parser.parse("AD-AXISBK",
                "Get up to Rs.5000 cashback! Pre-approved offer just for you."));
    }

    @Test
    public void ignores_bill_due_notifications() {
        assertNull(parser.parse("VK-ICICI",
                "Your credit card bill of Rs.10000 is due on 15-Jan. Minimum due Rs.500."));
    }

    @Test
    public void ignores_emi_reminders() {
        assertNull(parser.parse("AD-HDFCBK",
                "Your EMI of Rs.5000 will be deducted on 5th of next month"));
    }

    // ---------- Type classification ----------

    @Test
    public void classifies_debit_as_expense() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.500 debited from A/c at Amazon");
        assertNotNull(t);
        assertEquals("expense", t.getType());
    }

    @Test
    public void classifies_credit_as_income() {
        Transaction t = parser.parse("VM-SBIINB", "Rs.10000 credited to A/c by salary");
        assertNotNull(t);
        assertEquals("income", t.getType());
    }

    @Test
    public void classifies_spent_as_expense() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.250 spent on your card at Starbucks");
        assertNotNull(t);
        assertEquals("expense", t.getType());
    }

    @Test
    public void classifies_received_as_income() {
        Transaction t = parser.parse("VM-PAYTM", "Rs.500 received from John via UPI");
        assertNotNull(t);
        assertEquals("income", t.getType());
    }

    @Test
    public void rejects_message_with_amount_but_no_action_keyword() {
        // "balance is" has no debit/credit verb
        assertNull(parser.parse("VK-HDFCBK", "Your balance is Rs.50000"));
    }

    // ---------- Category routing ----------

    @Test
    public void categorizes_zomato_as_food() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.500 debited at Zomato Bangalore");
        assertEquals("Food & Dining", t.getCategory());
    }

    @Test
    public void categorizes_uber_as_transport() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.250 paid to Uber India");
        assertEquals("Transport", t.getCategory());
    }

    @Test
    public void categorizes_amazon_as_shopping() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.1500 spent at Amazon Pay");
        assertEquals("Shopping", t.getCategory());
    }

    @Test
    public void categorizes_netflix_as_entertainment() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.649 debited for netflix subscription");
        assertEquals("Entertainment", t.getCategory());
    }

    @Test
    public void categorizes_apollo_as_health() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.350 paid at apollo pharmacy");
        assertEquals("Health", t.getCategory());
    }

    @Test
    public void categorizes_zerodha_as_investments() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.10000 transferred to zerodha");
        assertEquals("Investments", t.getCategory());
    }

    @Test
    public void unknown_merchant_falls_back_to_other() {
        Transaction t = parser.parse("AD-HDFCBK", "Rs.500 debited at Random Merchant XYZ");
        // "merchant" doesn't hit any list, so we expect Other
        // (Also doesn't accidentally match "shop" / "mart" / etc.)
        assertEquals("Other", t.getCategory());
    }

    // ---------- Real-world bank SMS templates (no currency prefix) ----------

    /**
     * SBI UPI debit SMS. Note: the amount "5.00" has NO currency prefix —
     * it's anchored only by the verb "debited by". The tail of the message
     * contains a reference number (610166269276) and a support phone number
     * (1800111109) which MUST NOT be parsed as the amount.
     */
    @Test
    public void parses_sbi_upi_debit_without_currency_prefix() {
        String body = "Dear UPI user A/C X0620 debited by 5.00 on date 11Apr26 "
                + "trf to DIVY PUNIT PATEL Refno 610166269276 If not u? "
                + "call-1800111109 for other services-18001234-SBI";
        Transaction t = parser.parse("VM-SBIUPI", body);
        assertNotNull("SBI UPI debit must be recognised", t);
        assertEquals(5.00, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
    }

    @Test
    public void sbi_upi_message_does_not_pick_up_phone_number_as_amount() {
        String body = "Dear UPI user A/C X0620 debited by 5.00 on date 11Apr26 "
                + "trf to DIVY PUNIT PATEL Refno 610166269276 If not u? "
                + "call-1800111109 for other services-18001234-SBI";
        double amount = parser.extractAmount(body.toLowerCase());
        // Must be the 5.00 anchored to "debited by", NOT 610166269276 / 1800111109.
        assertEquals(5.00, amount, 0.001);
    }

    @Test
    public void parses_hdfc_sent_format_without_currency_prefix() {
        // HDFC UPI "Sent" template — amount follows the verb directly.
        Transaction t = parser.parse("AD-HDFCBK",
                "Sent Rs.250.00 From HDFC Bank A/C *1234 To JOHN DOE "
                        + "On 11-04-26 Ref 987654321. UPI");
        assertNotNull(t);
        assertEquals(250.00, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
    }

    @Test
    public void parses_icici_debited_with_format() {
        Transaction t = parser.parse("VM-ICICIB",
                "Acct XX123 debited with Rs 1,500.00 on 11-Apr-26; "
                        + "UPI/610166269276/Payment to merchant");
        assertNotNull(t);
        assertEquals(1500.00, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
    }

    @Test
    public void parses_axis_spent_using_card() {
        Transaction t = parser.parse("AD-AXISBK",
                "INR 750 spent using your Axis Bank Card xx1234 at AMAZON "
                        + "on 11-04-26. Avl Lmt INR 50000");
        assertNotNull(t);
        assertEquals(750.0, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
        assertEquals("Shopping", t.getCategory());
    }

    @Test
    public void parses_kotak_credited_salary() {
        Transaction t = parser.parse("VM-KOTAKB",
                "Rs.45000.00 credited to Kotak Bank A/C x1234 on 01-04-26 "
                        + "by NEFT ref 123456. Avl Bal Rs.50000");
        assertNotNull(t);
        assertEquals(45000.00, t.getAmount(), 0.001);
        assertEquals("income", t.getType());
    }

    @Test
    public void pnb_debit_without_currency_prefix_on_amount() {
        // PNB "Debited 500.00" — no currency token immediately before the number.
        Transaction t = parser.parse("VK-PNBSMS",
                "A/c X1234 Debited 500.00 on 11-04-26 Trf To JOHN Ref No 98765");
        assertNotNull(t);
        assertEquals(500.00, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
    }

    @Test
    public void extracts_amount_after_debited_by_without_currency() {
        assertEquals(5.00, parser.extractAmount("a/c x0620 debited by 5.00 on date"), 0.001);
    }

    @Test
    public void extracts_amount_after_credited_with_without_currency() {
        assertEquals(10000.0,
                parser.extractAmount("acct x123 credited with 10000 on 01-apr"), 0.001);
    }

    @Test
    public void does_not_pick_account_suffix_as_amount() {
        // "X0620" must not turn into 620.
        assertEquals(0.0, parser.extractAmount("dear upi user a/c x0620 balance enquiry"), 0.001);
    }

    @Test
    public void does_not_pick_reference_number_as_amount() {
        // Bare message with only a refno and no action verb → nothing to extract.
        assertEquals(0.0,
                parser.extractAmount("refno 610166269276 call-1800111109 for services"), 0.001);
    }

    @Test
    public void parses_indian_lakh_comma_format() {
        // "Rs.1,00,000.00" is how Indian banks print one lakh.
        Transaction t = parser.parse("AD-HDFCBK",
                "Rs.1,00,000.00 credited to A/c xx1234 by NEFT");
        assertNotNull(t);
        assertEquals(100000.0, t.getAmount(), 0.001);
        assertEquals("income", t.getType());
    }

    @Test
    public void parses_atm_cash_withdrawal() {
        Transaction t = parser.parse("VM-SBIINB",
                "Rs.2000 withdrawn at ATM on 11-Apr-26 A/c xx1234. Avl Bal Rs.30000");
        assertNotNull(t);
        assertEquals(2000.0, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
    }

    @Test
    public void parses_credit_card_transaction_of_format() {
        Transaction t = parser.parse("AD-HDFCBK",
                "Transaction of Rs.3500 on HDFC Credit Card xx1234 at FLIPKART on 11-Apr-26");
        assertNotNull(t);
        assertEquals(3500.0, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
        assertEquals("Shopping", t.getCategory());
    }

    @Test
    public void parses_paytm_wallet_debit() {
        Transaction t = parser.parse("VM-PAYTM",
                "Paid Rs.150 to Swiggy from your Paytm Wallet. UPI Ref 12345");
        assertNotNull(t);
        assertEquals(150.0, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
        assertEquals("Food & Dining", t.getCategory());
    }

    @Test
    public void balance_enquiry_with_large_numbers_is_ignored() {
        // No debit/credit verb → should be rejected even though "Rs.50000" exists.
        assertNull(parser.parse("VK-HDFCBK",
                "Your A/c xx1234 balance as on 11-Apr-26 is Rs.50000"));
    }

    // ---------- End-to-end happy path ----------

    @Test
    public void preserves_original_message_body_for_display() {
        String body = "Rs.499 debited from A/c xx1234 at Swiggy on 10-Jan";
        Transaction t = parser.parse("AD-HDFCBK", body);
        assertNotNull(t);
        assertEquals(body, t.getMessage());
        assertEquals(499.0, t.getAmount(), 0.001);
        assertEquals("expense", t.getType());
        assertEquals("Food & Dining", t.getCategory());
        assertTrue("Timestamp should be set", t.getTimestamp() > 0);
    }
}

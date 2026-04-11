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

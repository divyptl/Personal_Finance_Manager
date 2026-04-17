package com.example.personalfinancemanager;

import android.content.Context;
import android.net.Uri;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Writes the user's transaction history to a CSV file at a user-chosen Uri
 * (obtained via {@code ACTION_CREATE_DOCUMENT}).
 *
 * <p>We emit RFC 4180-compatible CSV (CRLF line endings, double-quoted fields,
 * escaped inner quotes) so the output opens cleanly in Excel on any OS.
 * Amounts are written without a currency symbol — the Excel format column
 * handles that — and we include a UTF-8 BOM so Excel on Windows doesn't
 * mangle currency symbols or non-ASCII merchant names.
 */
public final class TransactionCsvExporter {

    private TransactionCsvExporter() {}

    /**
     * @return number of rows written (not counting the header).
     * @throws Exception propagated so the caller can Toast a friendly error.
     */
    public static int export(Context ctx, Uri target, List<Transaction> rows) throws Exception {
        OutputStream out = ctx.getContentResolver().openOutputStream(target);
        if (out == null) throw new IllegalStateException("Could not open output stream");

        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);

        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            // UTF-8 BOM — forces Excel on Windows to read the file as UTF-8
            // instead of falling back to the legacy ANSI code page.
            w.write('\ufeff');
            w.write("Date,Type,Category,Amount,Note\r\n");
            for (Transaction t : rows) {
                w.write(dateFmt.format(new Date(t.getTimestamp())));
                w.write(',');
                w.write(csvQuote(normaliseType(t.getType())));
                w.write(',');
                w.write(csvQuote(t.getCategory()));
                w.write(',');
                w.write(String.format(Locale.ROOT, "%.2f", t.getAmount()));
                w.write(',');
                w.write(csvQuote(t.getMessage()));
                w.write("\r\n");
            }
        }
        return rows.size();
    }

    /** Map legacy "Debit"/"Credit" and new "expense"/"income" to a single canonical word. */
    private static String normaliseType(String type) {
        if (type == null) return "";
        if (type.equalsIgnoreCase("debit") || type.equalsIgnoreCase("expense")) return "Expense";
        if (type.equalsIgnoreCase("credit") || type.equalsIgnoreCase("income")) return "Income";
        return type;
    }

    /** Wrap in double-quotes, escaping any embedded double-quote per RFC 4180. */
    private static String csvQuote(String s) {
        if (s == null) return "\"\"";
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}

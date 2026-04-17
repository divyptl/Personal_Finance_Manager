package com.example.personalfinancemanager;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Shared dialog for adding a new manual transaction or editing an existing one.
 *
 * <p>Category list is kept in lock-step with {@link SmsTransactionParser} so
 * manually-entered transactions group together with SMS-captured ones on the
 * dashboard pie chart and count toward the right budget rows.
 *
 * <p>We intentionally store transaction "type" as "expense" / "income" —
 * the dashboard treats both "expense"/"Debit" and "income"/"Credit" as the
 * same, so either casing works, but we emit the lower-case form here to keep
 * the DB tidy going forward.
 */
public class TransactionEditorDialog {

    /**
     * Categories shown in the spinner. Order is "most-common-first" to
     * minimise the number of taps for the typical user. Must stay aligned
     * with {@link BudgetActivity}'s category list and the categories produced
     * by {@link SmsTransactionParser#categorize(String)}.
     */
    private static final String[] CATEGORIES = {
            "Food & Dining", "Transport", "Shopping", "Groceries",
            "Bills & Utilities", "Health", "Entertainment", "Education",
            "Rent & Housing", "Investments", "Transfer", "Other"
    };

    public interface OnSaveListener {
        /**
         * @param id        {@code null} for a new transaction; populated when editing
         * @param message   user's note (falls back to category if blank)
         * @param amount    positive amount in INR
         * @param timestamp ms since epoch
         * @param type      "expense" or "income"
         * @param category  one of {@link #CATEGORIES}
         */
        void onSave(@Nullable Integer id, String message, double amount,
                    long timestamp, String type, String category);
    }

    /** Show the editor; {@code existing} null ⇒ create; non-null ⇒ edit. */
    public static void show(Activity activity,
                            @Nullable Transaction existing,
                            OnSaveListener listener) {
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_transaction, null);

        RadioGroup radioType      = view.findViewById(R.id.radioType);
        RadioButton radioExpense  = view.findViewById(R.id.radioExpense);
        RadioButton radioIncome   = view.findViewById(R.id.radioIncome);
        EditText inputAmount      = view.findViewById(R.id.inputAmount);
        EditText inputNote        = view.findViewById(R.id.inputNote);
        Spinner spinnerCategory   = view.findViewById(R.id.spinnerCategory);
        TextView btnPickDate      = view.findViewById(R.id.btnPickDate);

        // Populate category spinner. Using simple_spinner_dropdown_item keeps
        // the look consistent with the month spinner on the dashboard.
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(categoryAdapter);

        // --- Prefill when editing ---
        final Calendar calendar = Calendar.getInstance();
        boolean isEdit = existing != null;
        if (isEdit) {
            inputAmount.setText(String.format(Locale.getDefault(), "%.2f", existing.getAmount()));
            inputNote.setText(existing.getMessage() == null ? "" : existing.getMessage());

            String type = existing.getType();
            boolean isIncome = type != null && (type.equalsIgnoreCase("income") || type.equalsIgnoreCase("credit"));
            if (isIncome) radioIncome.setChecked(true);
            else          radioExpense.setChecked(true);

            // Select the saved category if we still recognise it; otherwise
            // leave spinner on the first entry rather than crashing.
            int catIdx = indexOf(CATEGORIES, existing.getCategory());
            if (catIdx >= 0) spinnerCategory.setSelection(catIdx);

            calendar.setTimeInMillis(existing.getTimestamp());
        }

        // --- Date picker ---
        SimpleDateFormat dateFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        Runnable updateDateLabel = () -> btnPickDate.setText(dateFmt.format(calendar.getTime()));
        updateDateLabel.run();
        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(activity,
                    (datePicker, year, month, day) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, day);
                        updateDateLabel.run();
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
            // Don't let the user record transactions in the future — a common
            // slip when typing a date, and it breaks month-based aggregations.
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        int titleRes = isEdit
                ? R.string.dialog_edit_transaction_title
                : R.string.dialog_add_transaction_title;

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(R.string.action_save, null) // override below
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        // Override the positive button so validation errors can keep the dialog
        // open; setPositiveButton's default handler always dismisses.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String amountStr = inputAmount.getText().toString().trim();
                    double amount;
                    try {
                        amount = Double.parseDouble(amountStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(activity, R.string.error_amount_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (amount <= 0) {
                        Toast.makeText(activity, R.string.error_amount_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String note = inputNote.getText().toString().trim();
                    String category = (String) spinnerCategory.getSelectedItem();
                    if (note.isEmpty()) note = category; // keep the row readable

                    String type = radioType.getCheckedRadioButtonId() == R.id.radioIncome
                            ? "income" : "expense";

                    Integer id = isEdit ? existing.getId() : null;
                    listener.onSave(id, note, amount, calendar.getTimeInMillis(), type, category);
                    dialog.dismiss();
                }));

        dialog.show();
    }

    private static int indexOf(String[] arr, String needle) {
        if (needle == null) return -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equalsIgnoreCase(needle)) return i;
        }
        return -1;
    }
}

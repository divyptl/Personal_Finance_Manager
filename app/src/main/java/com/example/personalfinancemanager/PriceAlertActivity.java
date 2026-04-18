package com.example.personalfinancemanager;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Lets the user view / add / edit / delete price alerts. The list is driven
 * by {@link PriceAlertDao#getAllLive()} so edits made inside the dialog
 * reflect immediately without a manual refresh.
 *
 * <p>All writes go through the Room write executor to keep the UI thread free.
 */
public class PriceAlertActivity extends AppCompatActivity
        implements PriceAlertAdapter.Listener {

    private PriceAlertDao dao;
    private PriceAlertAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_alerts);

        dao = AppDatabase.getDatabase(this).priceAlertDao();

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ImageButton btnAdd = findViewById(R.id.btnAddAlert);
        btnAdd.setOnClickListener(v -> showEditor(null));

        emptyState = findViewById(R.id.emptyState);

        RecyclerView recycler = findViewById(R.id.recyclerAlerts);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PriceAlertAdapter(this);
        recycler.setAdapter(adapter);

        dao.getAllLive().observe(this, list -> {
            adapter.setItems(list);
            boolean empty = list == null || list.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
    }

    // ── adapter callbacks ────────────────────────────────────────────────

    @Override
    public void onToggle(PriceAlert alert, boolean enabled) {
        alert.setEnabled(enabled);
        AppDatabase.databaseWriteExecutor.execute(() -> dao.update(alert));
    }

    @Override
    public void onDelete(PriceAlert alert) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_alert_title)
                .setMessage(getString(R.string.dialog_delete_alert_message, alert.getTicker()))
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> dao.delete(alert));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onEdit(PriceAlert alert) { showEditor(alert); }

    // ── editor dialog ────────────────────────────────────────────────────

    private void showEditor(@Nullable PriceAlert existing) {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_price_alert, null, false);
        EditText inputTicker = view.findViewById(R.id.inputTicker);
        EditText inputLower  = view.findViewById(R.id.inputLower);
        EditText inputUpper  = view.findViewById(R.id.inputUpper);

        if (existing != null) {
            inputTicker.setText(existing.getTicker());
            inputTicker.setEnabled(false); // ticker is the logical identity
            if (existing.getLowerBound() != null) {
                inputLower.setText(String.valueOf(existing.getLowerBound()));
            }
            if (existing.getUpperBound() != null) {
                inputUpper.setText(String.valueOf(existing.getUpperBound()));
            }
        }

        int titleRes = existing == null
                ? R.string.dialog_add_alert_title
                : R.string.dialog_edit_alert_title;

        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String ticker = inputTicker.getText().toString().trim().toUpperCase();
                    if (TextUtils.isEmpty(ticker)) {
                        Toast.makeText(this, R.string.toast_ticker_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Double lower = parseOrNull(inputLower.getText().toString());
                    Double upper = parseOrNull(inputUpper.getText().toString());
                    if (lower == null && upper == null) {
                        Toast.makeText(this, R.string.toast_bounds_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (lower != null && upper != null && lower >= upper) {
                        Toast.makeText(this, R.string.toast_bounds_inverted,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    save(existing, ticker, lower, upper);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void save(@Nullable PriceAlert existing, String ticker,
                      @Nullable Double lower, @Nullable Double upper) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (existing == null) {
                PriceAlert fresh = new PriceAlert(ticker, lower, upper, 0L, true);
                dao.insert(fresh);
            } else {
                existing.setLowerBound(lower);
                existing.setUpperBound(upper);
                // Resetting lastNotifiedAt so the new threshold can fire
                // immediately rather than waiting out the 24h cooldown
                // inherited from a prior notification on the old bound.
                existing.setLastNotifiedAt(0L);
                dao.update(existing);
            }
        });
    }

    @Nullable
    private static Double parseOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
}

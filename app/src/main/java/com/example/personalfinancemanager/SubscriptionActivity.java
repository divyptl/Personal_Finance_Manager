package com.example.personalfinancemanager;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * "What am I paying for every month?" — runs {@link SubscriptionDetector}
 * against the entire transaction history and shows the result.
 *
 * <p>Detection runs on a background thread because it reads the full table
 * via {@code getAllTransactionsSync()}; the result is rendered on the UI
 * thread. No state is persisted — reopening re-runs the detector, so edits
 * to underlying transactions flow through immediately.
 */
public class SubscriptionActivity extends AppCompatActivity {

    private TextView textMonthlyOutgo;
    private TextView textSubsCount;
    private View emptyState;
    private SubscriptionAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscriptions);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        textMonthlyOutgo = findViewById(R.id.textMonthlyOutgo);
        textSubsCount    = findViewById(R.id.textSubsCount);
        emptyState       = findViewById(R.id.emptyState);

        RecyclerView recycler = findViewById(R.id.recyclerSubscriptions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubscriptionAdapter();
        recycler.setAdapter(adapter);

        loadSubscriptions();
    }

    private void loadSubscriptions() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            final List<Transaction> all = AppDatabase.getDatabase(this)
                    .transactionDao().getAllTransactionsSync();
            final List<Subscription> detected = SubscriptionDetector.detect(all);

            double monthlyTotal = 0;
            for (Subscription s : detected) monthlyTotal += s.monthlyEquivalent();
            final double totalOut = monthlyTotal;

            runOnUiThread(() -> {
                adapter.setItems(detected);
                textMonthlyOutgo.setText(String.format(Locale.getDefault(),
                        "\u20B9%.2f", totalOut));
                textSubsCount.setText(getResources().getQuantityString(
                        R.plurals.subscriptions_detected, detected.size(), detected.size()));
                emptyState.setVisibility(detected.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}

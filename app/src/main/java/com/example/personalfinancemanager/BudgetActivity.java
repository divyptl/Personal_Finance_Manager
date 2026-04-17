package com.example.personalfinancemanager;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows the user to create, view, and delete monthly budget limits per
 * expense category. Each budget row shows a progress bar with the current
 * month's spending vs. the limit, colour-coded green / amber / red.
 */
public class BudgetActivity extends AppCompatActivity {

    /**
     * Pre-defined expense categories. These MUST match the exact category strings
     * produced by {@link SmsTransactionParser#categorize(String)} — otherwise
     * budgets won't match incoming transactions and spending will show as ₹0.
     */
    private static final String[] CATEGORIES = {
            "Food & Dining", "Transport", "Shopping", "Groceries",
            "Bills & Utilities", "Health", "Entertainment", "Education",
            "Rent & Housing", "Investments", "Transfer", "Other"
    };

    private BudgetAdapter adapter;
    private View emptyState;
    private List<Budget> currentBudgets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        ImageButton btnBack = findViewById(R.id.btnBack);
        RecyclerView recycler = findViewById(R.id.recyclerBudgets);
        emptyState = findViewById(R.id.emptyState);
        FloatingActionButton fab = findViewById(R.id.fabAddBudget);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BudgetAdapter();
        recycler.setAdapter(adapter);

        adapter.setOnBudgetLongClickListener((budget, pos) ->
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_delete_budget_title, budget.getCategory()))
                        .setMessage(R.string.dialog_delete_budget_message)
                        .setPositiveButton(R.string.action_remove, (d, w) ->
                                AppDatabase.databaseWriteExecutor.execute(() ->
                                        AppDatabase.getDatabase(this).budgetDao()
                                                .deleteBudget(budget.getCategory())))
                        .setNegativeButton(R.string.action_cancel, null)
                        .show());

        // Observe budgets and spending. We hold the latest budget list in a
        // local ref so the transaction observer (below) can recompute spending
        // without re-querying Budget rows.
        AppDatabase db = AppDatabase.getDatabase(this);
        db.budgetDao().getAllBudgets().observe(this, budgets -> {
            currentBudgets = budgets;
            adapter.setBudgets(budgets);
            boolean empty = budgets == null || budgets.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recycler.setVisibility(empty ? View.GONE : View.VISIBLE);

            if (budgets != null && !budgets.isEmpty()) {
                loadSpending(budgets);
            }
        });

        // Also refresh spending whenever transactions change — otherwise the
        // "Spent" values freeze at the value they had when the activity opened
        // and new SMS-captured expenses won't appear until the user navigates
        // away and back.
        db.transactionDao().getAllTransactions().observe(this, txns -> {
            if (currentBudgets != null && !currentBudgets.isEmpty()) {
                loadSpending(currentBudgets);
            }
        });

        btnBack.setOnClickListener(v -> finish());
        fab.setOnClickListener(v -> showAddBudgetDialog());
    }

    private void loadSpending(List<Budget> budgets) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long monthStart = BudgetChecker.startOfCurrentMonth();
            List<CategorySum> sums = AppDatabase.getDatabase(this)
                    .transactionDao().getCategoryExpensesSinceSync(monthStart);
            Map<String, Double> spentMap = new HashMap<>();
            if (sums != null) {
                for (CategorySum cs : sums) {
                    spentMap.put(cs.category, cs.total);
                }
            }
            runOnUiThread(() -> adapter.setSpentMap(spentMap));
        });
    }

    private void showAddBudgetDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_budget, null);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerCategory);
        EditText inputLimit = dialogView.findViewById(R.id.inputLimit);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(catAdapter);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_add_budget_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String category = spinnerCategory.getSelectedItem().toString();
                    String limitStr = inputLimit.getText().toString().trim();
                    if (limitStr.isEmpty()) {
                        Toast.makeText(this, R.string.error_budget_limit_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        double limit = Double.parseDouble(limitStr);
                        if (limit <= 0) {
                            Toast.makeText(this, R.string.error_positive_numbers,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        AppDatabase.databaseWriteExecutor.execute(() ->
                                AppDatabase.getDatabase(this).budgetDao()
                                        .insertOrUpdate(new Budget(category, limit)));
                        Toast.makeText(this,
                                getString(R.string.toast_budget_set, category),
                                Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.error_invalid_number,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}

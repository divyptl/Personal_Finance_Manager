package com.example.personalfinancemanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TransactionViewModel transactionViewModel;
    private StockViewModel stockViewModel;
    private TransactionAdapter adapter;

    private TextView textTotalBalance, textCardTitle, textSwipeHint;
    private PieChart pieChart;
    private Spinner spinnerMonth;

    private double currentMonthExpenses = 0.0;
    private double totalPortfolioInvested = 0.0;
    private boolean isShowingPortfolio = false;

    private final List<Long> startDates = new ArrayList<>();
    private final List<Long> endDates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link UI
        textTotalBalance = findViewById(R.id.textTotalBalance);
        textCardTitle = findViewById(R.id.textCardTitle);
        textSwipeHint = findViewById(R.id.textSwipeHint);
        pieChart = findViewById(R.id.pieChart);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        ImageButton btnReset = findViewById(R.id.btnReset);
        View btnOpenPortfolio = findViewById(R.id.btnOpenPortfolio);

        // RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        setupPieChart();

        // ViewModels
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);

        // Observe stocks for net worth
        stockViewModel.getAllStocks().observe(this, stocks -> {
            totalPortfolioInvested = 0.0;
            if (stocks != null) {
                for (Stock s : stocks) {
                    totalPortfolioInvested += (s.getQuantity() * s.getAverageBuyPrice());
                }
            }
            updateCardDisplay();
        });

        setupMonthSpinner();
        setupSwipeListener();

        // Button clicks
        btnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All Transactions?")
                    .setMessage("This will permanently delete your entire transaction history.")
                    .setPositiveButton("Delete", (dialog, which) -> transactionViewModel.deleteAllTransactions())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnOpenPortfolio.setOnClickListener(v ->
                startActivity(new Intent(this, PortfolioActivity.class)));
    }

    private void setupMonthSpinner() {
        List<String> monthNames = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

        for (int i = 0; i < 6; i++) {
            Calendar endCal = (Calendar) cal.clone();
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endCal.set(Calendar.SECOND, 59);
            endDates.add(endCal.getTimeInMillis());

            Calendar startCal = (Calendar) cal.clone();
            startCal.set(Calendar.DAY_OF_MONTH, 1);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startCal.set(Calendar.SECOND, 0);
            startDates.add(startCal.getTimeInMillis());

            monthNames.add(sdf.format(cal.getTime()));
            cal.add(Calendar.MONTH, -1);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, monthNames);
        spinnerMonth.setAdapter(spinnerAdapter);

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent.getChildAt(0) instanceof TextView) {
                    ((TextView) parent.getChildAt(0)).setTextColor(
                            ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                }
                long start = startDates.get(position);
                long end = endDates.get(position);
                transactionViewModel.getTransactionsByMonth(start, end).observe(
                        MainActivity.this, transactions -> {
                            adapter.setTransactions(transactions);
                            calculateDashboard(transactions);
                        });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSwipeListener() {
        View cardBalance = findViewById(R.id.cardBalance);
        // Tap to toggle between Expenses and Net Worth
        // (swipe gestures conflict with NestedScrollView scrolling)
        cardBalance.setOnClickListener(v -> {
            isShowingPortfolio = !isShowingPortfolio;
            updateCardDisplay();
        });
    }

    private void updateCardDisplay() {
        if (!isShowingPortfolio) {
            textCardTitle.setText(R.string.label_total_expenses);
            textTotalBalance.setText(String.format(Locale.getDefault(), "\u20B9%.2f", currentMonthExpenses));
            textTotalBalance.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            textSwipeHint.setText("Tap to view net worth");
        } else {
            textCardTitle.setText(R.string.label_invested_net_worth);
            double netWorth = totalPortfolioInvested - currentMonthExpenses;
            textTotalBalance.setText(String.format(Locale.getDefault(), "\u20B9%.2f", netWorth));

            int color = netWorth >= 0 ? R.color.accent_green : R.color.accent_red;
            textTotalBalance.setTextColor(ContextCompat.getColor(this, color));
            textSwipeHint.setText("Tap to view expenses");
        }
    }

    private void calculateDashboard(List<Transaction> transactions) {
        currentMonthExpenses = 0.0;
        Map<String, Float> categoryMap = new HashMap<>();

        for (Transaction t : transactions) {
            String type = t.getType();
            if (type != null && (type.equalsIgnoreCase("Debit") || type.equalsIgnoreCase("expense"))) {
                currentMonthExpenses += t.getAmount();
                String category = t.getCategory() != null ? t.getCategory() : "Other";
                float current = categoryMap.getOrDefault(category, 0f);
                categoryMap.put(category, current + (float) t.getAmount());
            }
        }

        updateCardDisplay();

        // Pie chart
        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : categoryMap.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(
                ContextCompat.getColor(this, R.color.accent_purple),
                ContextCompat.getColor(this, R.color.accent_cyan),
                ContextCompat.getColor(this, R.color.accent_red),
                ContextCompat.getColor(this, R.color.accent_amber),
                ContextCompat.getColor(this, R.color.accent_blue),
                ContextCompat.getColor(this, R.color.accent_green)
        );
        dataSet.setValueTextColor(ContextCompat.getColor(this, R.color.text_primary));
        dataSet.setValueTextSize(11f);
        dataSet.setSliceSpace(2f);
        dataSet.setValueFormatter(new PercentFormatter(pieChart));

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.invalidate();
    }

    private void setupPieChart() {
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(ContextCompat.getColor(this, R.color.bg_dark));
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(0f);
        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        pieChart.setCenterTextSize(13f);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setRotationEnabled(false);

        Legend legend = pieChart.getLegend();
        legend.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setFormSize(10f);
        legend.setXEntrySpace(14f);
    }
}

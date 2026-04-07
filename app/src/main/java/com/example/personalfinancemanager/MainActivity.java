package com.example.personalfinancemanager;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.button.MaterialButton;

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

    private TextView textTotalBalance;
    private TextView textCardTitle;
    private TextView textSwipeHint;
    private PieChart pieChart;
    private Spinner spinnerMonth;

    // Data Trackers
    private double currentMonthExpenses = 0.0;
    private double totalPortfolioInvested = 0.0;
    private boolean isShowingPortfolio = false; // False = Expenses, True = Net Worth

    // Month Data
    private List<Long> startDates = new ArrayList<>();
    private List<Long> endDates = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Link UI
        textTotalBalance = findViewById(R.id.textTotalBalance);
        textCardTitle = findViewById(R.id.textCardTitle);
        textSwipeHint = findViewById(R.id.textSwipeHint);
        pieChart = findViewById(R.id.pieChart);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        ImageButton btnReset = findViewById(R.id.btnReset);
        MaterialButton btnOpenPortfolio = findViewById(R.id.btnOpenPortfolio);

        // 2. Setup RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        setupPieChart();

        // 3. Initialize ViewModels (We need both now!)
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);


        // 4. Observe Stock Portfolio to calculate Net Worth
        stockViewModel.getAllStocks().observe(this, stocks -> {
            totalPortfolioInvested = 0.0;

            if (stocks != null) {
                for (Stock s : stocks) {
                    totalPortfolioInvested += (s.getQuantity() * s.getAverageBuyPrice());
                }
            }
            updateCardDisplay(); // Refresh the card if it's showing Net Worth
        });

        // 5. Setup Month Dropdown & Logic
        setupMonthSpinner();

        // 6. Setup Swipe Detection on the Card
        setupSwipeListener();

        // 7. Button Clicks
        btnReset.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Wipe Data?")
                    .setMessage("This will permanently delete your entire transaction history.")
                    .setPositiveButton("Delete", (dialog, which) -> transactionViewModel.deleteAllTransactions())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnOpenPortfolio.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PortfolioActivity.class)));
    }

    // --- SPINNER LOGIC ---
    private void setupMonthSpinner() {
        List<String> monthNames = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

        // Generate the last 6 months
        for (int i = 0; i < 6; i++) {
            // End of Month
            Calendar endCal = (Calendar) cal.clone();
            endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
            endCal.set(Calendar.HOUR_OF_DAY, 23);
            endCal.set(Calendar.MINUTE, 59);
            endDates.add(endCal.getTimeInMillis());

            // Start of Month
            Calendar startCal = (Calendar) cal.clone();
            startCal.set(Calendar.DAY_OF_MONTH, 1);
            startCal.set(Calendar.HOUR_OF_DAY, 0);
            startCal.set(Calendar.MINUTE, 0);
            startDates.add(startCal.getTimeInMillis());

            monthNames.add(sdf.format(cal.getTime()));
            cal.add(Calendar.MONTH, -1); // Go back one month
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, monthNames);
        spinnerMonth.setAdapter(spinnerAdapter);

        // When a user selects a month, fetch that month's data!
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // To keep text white in the dark theme spinner
                if (parent.getChildAt(0) != null) {
                    ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
                }

                // Fetch the data for the selected timeframe
                long start = startDates.get(position);
                long end = endDates.get(position);

                // Stop observing old data, start observing new timeframe
                transactionViewModel.getTransactionsByMonth(start, end).observe(MainActivity.this, transactions -> {
                    adapter.setTransactions(transactions);
                    calculateDashboard(transactions);
                });
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // --- SWIPE LOGIC ---
    private void setupSwipeListener() {
        View cardBalance = findViewById(R.id.cardBalance);
        GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 100) { // If swiped horizontally
                    isShowingPortfolio = !isShowingPortfolio; // Toggle state
                    updateCardDisplay();
                    return true;
                }
                return false;
            }
        });

        cardBalance.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    // --- DASHBOARD DISPLAY UPDATE ---
    private void updateCardDisplay() {
        if (!isShowingPortfolio) {
            textCardTitle.setText("Total Expenses");
            textTotalBalance.setText(String.format("₹%.2f", currentMonthExpenses));
            textTotalBalance.setTextColor(Color.WHITE);
            textSwipeHint.setText("● ○"); // Dot indicator
        } else {
            textCardTitle.setText("Invested Net Worth");
            double netWorth = totalPortfolioInvested - currentMonthExpenses;
            textTotalBalance.setText(String.format("₹%.2f", netWorth));

            // Turn green if you're positive, red if you've spent more than you invested
            if (netWorth >= 0) textTotalBalance.setTextColor(Color.parseColor("#4CAF50"));
            else textTotalBalance.setTextColor(Color.parseColor("#FF5252"));

            textSwipeHint.setText("○ ●");
        }
    }

    // --- MATH ENGINE ---
    private void calculateDashboard(List<Transaction> transactions) {
        currentMonthExpenses = 0.0;
        Map<String, Float> categoryMap = new HashMap<>();

        for (Transaction t : transactions) {
            String type = t.getType();
            if (type != null && (type.equalsIgnoreCase("Debit") || type.equalsIgnoreCase("expense"))) {
                currentMonthExpenses += t.getAmount();

                String category = t.getCategory() != null ? t.getCategory() : "Other";
                float currentCatTotal = categoryMap.getOrDefault(category, 0f);
                categoryMap.put(category, currentCatTotal + (float) t.getAmount());
            }
        }

        updateCardDisplay(); // Update the UI numbers

        // Update Pie Chart
        ArrayList<PieEntry> pieEntries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : categoryMap.entrySet()) pieEntries.add(new PieEntry(entry.getValue(), entry.getKey()));

        PieDataSet dataSet = new PieDataSet(pieEntries, "");
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#BB86FC"));
        colors.add(Color.parseColor("#03DAC5"));
        colors.add(Color.parseColor("#CF6679"));
        colors.add(Color.parseColor("#FFB74D"));
        colors.add(Color.parseColor("#64B5F6"));
        dataSet.setColors(colors);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        PieData data = new PieData(dataSet);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    private void setupPieChart() {
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#121212"));
        pieChart.setTransparentCircleRadius(0f);
        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.getDescription().setEnabled(false);
        Legend legend = pieChart.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }
}
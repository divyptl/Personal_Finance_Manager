package com.example.personalfinancemanager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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

    private static final int REQ_NOTIFICATIONS = 1001;
    private static final int REQ_SMS = 1002;

    private TransactionViewModel transactionViewModel;
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

        // Permission rationales — Play Store policy requires that we explain
        // BEFORE prompting why we need each permission. We use AlertDialogs
        // here as a lightweight pre-prompt; an in-app onboarding screen is
        // a P3 polish item.
        requestNotificationPermissionWithRationale();
        requestSmsPermissionWithRationale();

        textTotalBalance = findViewById(R.id.textTotalBalance);
        textCardTitle = findViewById(R.id.textCardTitle);
        textSwipeHint = findViewById(R.id.textSwipeHint);
        pieChart = findViewById(R.id.pieChart);
        spinnerMonth = findViewById(R.id.spinnerMonth);
        ImageButton btnReset = findViewById(R.id.btnReset);
        View btnOpenPortfolio = findViewById(R.id.btnOpenPortfolio);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        recyclerView.setAdapter(adapter);

        setupPieChart();

        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        StockViewModel stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);

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

        btnReset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        transactionViewModel.deleteAllTransactions())
                .setNegativeButton(R.string.action_cancel, null)
                .show());

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
            textSwipeHint.setText(R.string.label_tap_net_worth);
        } else {
            textCardTitle.setText(R.string.label_invested_net_worth);
            double netWorth = totalPortfolioInvested - currentMonthExpenses;
            textTotalBalance.setText(String.format(Locale.getDefault(), "\u20B9%.2f", netWorth));
            int color = netWorth >= 0 ? R.color.accent_green : R.color.accent_red;
            textTotalBalance.setTextColor(ContextCompat.getColor(this, color));
            textSwipeHint.setText(R.string.label_tap_expenses);
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
        pieChart.setCenterText(getString(R.string.label_expenses_short));
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

    // ==================== PERMISSIONS ====================

    private void requestNotificationPermissionWithRationale() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rationale_notifications_title)
                    .setMessage(R.string.rationale_notifications_message)
                    .setPositiveButton(R.string.action_continue, (d, w) -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                    .setNegativeButton(R.string.action_no_thanks, null)
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void requestSmsPermissionWithRationale() {
        boolean receiveGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        boolean readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        if (receiveGranted && readGranted) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.rationale_sms_title)
                .setMessage(R.string.rationale_sms_message)
                .setPositiveButton(R.string.action_grant, (d, w) -> ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS},
                        REQ_SMS))
                .setNegativeButton(R.string.action_no_thanks, null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Currently we don't need to react — the receiver/notifications simply
        // start working once the user grants permission. Hook here to surface
        // a Snackbar if you want to confirm.
    }
}

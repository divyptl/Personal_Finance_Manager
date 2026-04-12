package com.example.personalfinancemanager;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Visualization Dashboard showing:
 * <ol>
 *   <li>A <b>line chart</b> of monthly expense vs. income totals (12 months)</li>
 *   <li>A <b>stacked bar chart</b> of expenses broken down by category per month</li>
 * </ol>
 *
 * <p>All data comes from synchronous DAO queries run on the Room write executor.
 * No new architectural patterns — just new queries + a screen.
 */
public class AnalyticsActivity extends AppCompatActivity {

    private LineChart lineChart;
    private BarChart barChart;
    private View emptyState;

    /** Category → colour mapping. Consistent with the pie chart on the main screen. */
    private final int[] CATEGORY_COLORS = new int[]{
            0xFFA78BFA, // purple
            0xFF22D3EE, // cyan
            0xFFF87171, // red
            0xFFFBBF24, // amber
            0xFF60A5FA, // blue
            0xFF34D399, // green
            0xFFE879F9, // pink
            0xFFFB923C, // orange
            0xFF94A3B8, // grey
            0xFF818CF8  // indigo
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        ImageButton btnBack = findViewById(R.id.btnBack);
        lineChart = findViewById(R.id.lineChart);
        barChart = findViewById(R.id.barChart);
        emptyState = findViewById(R.id.emptyState);

        btnBack.setOnClickListener(v -> finish());

        styleLineChart();
        styleBarChart();

        loadData();
    }

    // ==================== DATA LOADING ====================

    private void loadData() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 12 months back from now
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -11);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long sinceMillis = cal.getTimeInMillis();

            TransactionDao dao = AppDatabase.getDatabase(this).transactionDao();
            List<MonthlyTotal> expenseTotals = dao.getMonthlyExpenseTotals(sinceMillis);
            List<MonthlyTotal> incomeTotals = dao.getMonthlyIncomeTotals(sinceMillis);
            List<MonthlyCategoryTotal> categoryTotals = dao.getMonthlyCategoryExpenses(sinceMillis);

            runOnUiThread(() -> {
                boolean hasData = (expenseTotals != null && !expenseTotals.isEmpty())
                        || (incomeTotals != null && !incomeTotals.isEmpty());

                if (!hasData) {
                    emptyState.setVisibility(View.VISIBLE);
                    lineChart.setVisibility(View.GONE);
                    barChart.setVisibility(View.GONE);
                    return;
                }

                emptyState.setVisibility(View.GONE);
                lineChart.setVisibility(View.VISIBLE);
                barChart.setVisibility(View.VISIBLE);

                populateLineChart(expenseTotals, incomeTotals);
                populateBarChart(categoryTotals);
            });
        });
    }

    // ==================== LINE CHART ====================

    private void populateLineChart(List<MonthlyTotal> expenses,
                                   List<MonthlyTotal> incomes) {
        // Merge all months into a sorted list
        Set<String> allMonths = new LinkedHashSet<>();
        if (expenses != null) for (MonthlyTotal mt : expenses) allMonths.add(mt.month);
        if (incomes != null)  for (MonthlyTotal mt : incomes) allMonths.add(mt.month);

        List<String> months = new ArrayList<>(allMonths);
        java.util.Collections.sort(months);

        Map<String, Double> expenseMap = toMap(expenses);
        Map<String, Double> incomeMap = toMap(incomes);

        List<Entry> expenseEntries = new ArrayList<>();
        List<Entry> incomeEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < months.size(); i++) {
            String m = months.get(i);
            labels.add(formatMonthLabel(m));
            expenseEntries.add(new Entry(i, expenseMap.getOrDefault(m, 0.0).floatValue()));
            incomeEntries.add(new Entry(i, incomeMap.getOrDefault(m, 0.0).floatValue()));
        }

        // Expense line
        LineDataSet expenseDs = new LineDataSet(expenseEntries,
                getString(R.string.label_expenses));
        expenseDs.setColor(ContextCompat.getColor(this, R.color.accent_red));
        expenseDs.setCircleColor(ContextCompat.getColor(this, R.color.accent_red));
        expenseDs.setLineWidth(2f);
        expenseDs.setCircleRadius(3f);
        expenseDs.setDrawValues(false);
        expenseDs.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        // Income line
        LineDataSet incomeDs = new LineDataSet(incomeEntries,
                getString(R.string.label_income));
        incomeDs.setColor(ContextCompat.getColor(this, R.color.accent_green));
        incomeDs.setCircleColor(ContextCompat.getColor(this, R.color.accent_green));
        incomeDs.setLineWidth(2f);
        incomeDs.setCircleRadius(3f);
        incomeDs.setDrawValues(false);
        incomeDs.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        lineChart.setData(new LineData(expenseDs, incomeDs));
        lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        lineChart.getXAxis().setLabelCount(Math.min(labels.size(), 6));
        lineChart.invalidate();
    }

    // ==================== STACKED BAR CHART ====================

    private void populateBarChart(List<MonthlyCategoryTotal> data) {
        if (data == null || data.isEmpty()) {
            barChart.setVisibility(View.GONE);
            return;
        }

        // Collect ordered months and all categories
        LinkedHashMap<String, Map<String, Double>> monthMap = new LinkedHashMap<>();
        Set<String> allCategories = new LinkedHashSet<>();

        for (MonthlyCategoryTotal mct : data) {
            allCategories.add(mct.category);
            monthMap.computeIfAbsent(mct.month, k -> new HashMap<>())
                    .put(mct.category, mct.total);
        }

        List<String> months = new ArrayList<>(monthMap.keySet());
        List<String> categories = new ArrayList<>(allCategories);
        List<String> labels = new ArrayList<>();

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            labels.add(formatMonthLabel(months.get(i)));
            Map<String, Double> catMap = monthMap.get(months.get(i));
            float[] values = new float[categories.size()];
            for (int c = 0; c < categories.size(); c++) {
                values[c] = catMap.getOrDefault(categories.get(c), 0.0).floatValue();
            }
            entries.add(new BarEntry(i, values));
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        // Assign colours per stack (category)
        int[] colors = new int[categories.size()];
        for (int c = 0; c < categories.size(); c++) {
            colors[c] = CATEGORY_COLORS[c % CATEGORY_COLORS.length];
        }
        dataSet.setColors(colors);
        dataSet.setStackLabels(categories.toArray(new String[0]));
        dataSet.setDrawValues(false);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        barChart.setData(barData);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.getXAxis().setLabelCount(Math.min(labels.size(), 6));
        barChart.invalidate();
    }

    // ==================== CHART STYLING ====================

    private void styleLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setExtraBottomOffset(8f);

        int textColor = ContextCompat.getColor(this, R.color.text_secondary);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(textColor);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#1E2A3A"));
        leftAxis.setAxisMinimum(0f);

        lineChart.getAxisRight().setEnabled(false);

        Legend legend = lineChart.getLegend();
        legend.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        legend.setTextSize(11f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
    }

    private void styleBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setTouchEnabled(true);
        barChart.setDragEnabled(true);
        barChart.setScaleEnabled(false);
        barChart.setExtraBottomOffset(8f);

        int textColor = ContextCompat.getColor(this, R.color.text_secondary);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setTextColor(textColor);
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#1E2A3A"));
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);

        Legend legend = barChart.getLegend();
        legend.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        legend.setTextSize(10f);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setWordWrapEnabled(true);
    }

    // ==================== HELPERS ====================

    private static Map<String, Double> toMap(List<MonthlyTotal> list) {
        Map<String, Double> map = new HashMap<>();
        if (list != null) {
            for (MonthlyTotal mt : list) map.put(mt.month, mt.total);
        }
        return map;
    }

    /**
     * Converts "2026-04" → "Apr" for compact x-axis labels.
     */
    private String formatMonthLabel(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            int monthNum = Integer.parseInt(parts[1]);
            String[] abbr = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return abbr[monthNum - 1];
        } catch (Exception e) {
            return yearMonth;
        }
    }
}

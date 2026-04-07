package com.example.personalfinancemanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class PortfolioActivity extends AppCompatActivity {

    private StockViewModel stockViewModel;
    private StockAdapter adapter;
    private TextView tvTotalInvested;
    private TextView tvCurrentValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portfolio);

        tvTotalInvested = findViewById(R.id.tvTotalInvested);
        tvCurrentValue = findViewById(R.id.tvCurrentValue);
        FloatingActionButton fabAddStock = findViewById(R.id.fabAddStock);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewPortfolio);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StockAdapter();
        recyclerView.setAdapter(adapter);

        stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);

        // Calculate Total Invested instantly from Database
        stockViewModel.getAllStocks().observe(this, stocks -> {
            adapter.setStocks(stocks);
            double totalInvested = 0.0;
            for (Stock s : stocks) {
                totalInvested += (s.getQuantity() * s.getAverageBuyPrice());
            }
            tvTotalInvested.setText(String.format("₹%.2f", totalInvested));
        });

        // The Dual-Option FAB
        fabAddStock.setOnClickListener(v -> {
            String[] options = {"📝 Add Stock Manually", "📄 Upload CAS Statement (PDF)"};
            new AlertDialog.Builder(this)
                    .setTitle("Update Portfolio")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) showManualEntryDialog();
                        else if (which == 1) startPdfUploadFlow();
                    }).show();
        });
    }

    private void showManualEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputTicker = new EditText(this);
        inputTicker.setHint("Stock Ticker (e.g., RELIANCE)");
        layout.addView(inputTicker);

        final EditText inputQuantity = new EditText(this);
        inputQuantity.setHint("Quantity");
        inputQuantity.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputQuantity);

        final EditText inputPrice = new EditText(this);
        inputPrice.setHint("Average Buy Price (₹)");
        inputPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputPrice);

        new AlertDialog.Builder(this)
                .setTitle("Add Stock")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String ticker = inputTicker.getText().toString().toUpperCase().trim();
                    String qtyStr = inputQuantity.getText().toString();
                    String priceStr = inputPrice.getText().toString();

                    if (!ticker.isEmpty() && !qtyStr.isEmpty() && !priceStr.isEmpty()) {
                        double quantity = Double.parseDouble(qtyStr);
                        double price = Double.parseDouble(priceStr);

                        // Saving with "MANUAL" as the broker name
                        stockViewModel.buyStock(ticker, "N/A", quantity, price, "MANUAL");
                        Toast.makeText(this, ticker + " added!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startPdfUploadFlow() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select CAS Statement"), 101);
            Toast.makeText(this, "Select your CDSL/NSDL PDF", Toast.LENGTH_SHORT).show();
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }
}
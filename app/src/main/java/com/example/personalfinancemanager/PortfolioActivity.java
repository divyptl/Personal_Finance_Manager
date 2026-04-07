package com.example.personalfinancemanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PortfolioActivity extends AppCompatActivity {

    private static final String TAG = "PortfolioActivity";

    private StockViewModel stockViewModel;
    private StockAdapter adapter;
    private CredentialManager credentialManager;

    private TextView tvTotalInvested, tvCurrentValue, tvTotalPnl, tvPnlPercent;
    private ProgressBar progressSync;
    private ImageButton btnSync, btnSettings;
    private View emptyState;

    private List<Stock> currentStocks;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri pdfUri = result.getData().getData();
                    if (pdfUri != null) {
                        showPdfPasswordDialog(pdfUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portfolio);

        // Initialize PDFBox
        PDFBoxResourceLoader.init(getApplicationContext());

        credentialManager = new CredentialManager(this);

        // Link UI
        tvTotalInvested = findViewById(R.id.tvTotalInvested);
        tvCurrentValue = findViewById(R.id.tvCurrentValue);
        tvTotalPnl = findViewById(R.id.tvTotalPnl);
        tvPnlPercent = findViewById(R.id.tvPnlPercent);
        progressSync = findViewById(R.id.progressSync);
        btnSync = findViewById(R.id.btnSync);
        btnSettings = findViewById(R.id.btnSettings);
        emptyState = findViewById(R.id.emptyState);
        FloatingActionButton fabAddStock = findViewById(R.id.fabAddStock);
        ImageButton btnBack = findViewById(R.id.btnBack);

        // RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerViewPortfolio);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StockAdapter();
        recyclerView.setAdapter(adapter);

        // Long-click to delete stock
        adapter.setOnStockLongClickListener((stock, position) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remove " + stock.getTicker() + "?")
                    .setMessage("This will remove the stock from your portfolio.")
                    .setPositiveButton("Remove", (d, w) -> {
                        StockRepository repo = new StockRepository(getApplication());
                        AppDatabase.databaseWriteExecutor.execute(() ->
                                AppDatabase.getDatabase(this).stockDao().deleteStock(stock.getTicker()));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ViewModel
        stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);

        stockViewModel.getAllStocks().observe(this, stocks -> {
            currentStocks = stocks;
            adapter.setStocks(stocks);

            // Show/hide empty state
            if (stocks == null || stocks.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
            } else {
                emptyState.setVisibility(View.GONE);
            }

            // Calculate totals from DB
            double totalInvested = 0.0;
            for (Stock s : stocks) {
                totalInvested += (s.getQuantity() * s.getAverageBuyPrice());
            }
            tvTotalInvested.setText(formatCurrency(totalInvested));

            // Fetch live prices if Angel One is configured
            if (credentialManager.isConfigured() && !stocks.isEmpty()) {
                fetchLivePrices(stocks, totalInvested);
            } else {
                tvCurrentValue.setText(formatCurrency(totalInvested));
                tvTotalPnl.setText("\u20B90.00");
                tvPnlPercent.setText("(0.00%)");
            }
        });

        // Button clicks
        btnBack.setOnClickListener(v -> finish());

        btnSync.setOnClickListener(v -> syncAngelOneHoldings());

        btnSettings.setOnClickListener(v -> showCredentialsDialog());

        fabAddStock.setOnClickListener(v -> showAddOptionsSheet());
    }

    // --- LIVE PRICE FETCHING ---

    private void fetchLivePrices(List<Stock> stocks, double totalInvested) {
        showSyncProgress(true);

        AngelOneHelper helper = new AngelOneHelper(credentialManager);
        helper.fetchBatchLtp(stocks, priceMap -> {
            showSyncProgress(false);
            adapter.setPrices(priceMap);

            double currentValue = adapter.getTotalCurrentValue();
            double pnl = currentValue - totalInvested;
            double pnlPercent = totalInvested > 0 ? (pnl / totalInvested) * 100 : 0;

            tvCurrentValue.setText(formatCurrency(currentValue));
            tvTotalPnl.setText(String.format(Locale.getDefault(), "%s\u20B9%.2f",
                    pnl >= 0 ? "+" : "", pnl));
            tvPnlPercent.setText(String.format(Locale.getDefault(), "(%.2f%%)", pnlPercent));

            int color = pnl >= 0
                    ? getColor(R.color.accent_green)
                    : getColor(R.color.accent_red);
            tvTotalPnl.setTextColor(color);
            tvPnlPercent.setTextColor(color);
            tvCurrentValue.setTextColor(color);
        });
    }

    // --- ANGEL ONE SYNC ---

    private void syncAngelOneHoldings() {
        if (!credentialManager.isConfigured()) {
            showCredentialsDialog();
            return;
        }

        showSyncProgress(true);
        Toast.makeText(this, "Syncing with Angel One...", Toast.LENGTH_SHORT).show();

        AngelOneHelper helper = new AngelOneHelper(credentialManager);
        helper.fetchMyHoldings(holdings -> {
            showSyncProgress(false);
            if (holdings.isEmpty()) {
                Toast.makeText(this, "No holdings found", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Stock stock : holdings) {
                stockViewModel.buyStock(
                        stock.getTicker(),
                        stock.getSymbolToken(),
                        stock.getQuantity(),
                        stock.getAverageBuyPrice(),
                        "AngelOne"
                );
            }

            Toast.makeText(this,
                    holdings.size() + " stocks synced from Angel One",
                    Toast.LENGTH_SHORT).show();
        });
    }

    // --- ADD OPTIONS BOTTOM SHEET ---

    private void showAddOptionsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_add_stock, null);
        sheet.setContentView(sheetView);

        sheetView.findViewById(R.id.optionManual).setOnClickListener(v -> {
            sheet.dismiss();
            showManualEntryDialog();
        });

        sheetView.findViewById(R.id.optionPdf).setOnClickListener(v -> {
            sheet.dismiss();
            startPdfUploadFlow();
        });

        sheetView.findViewById(R.id.optionSync).setOnClickListener(v -> {
            sheet.dismiss();
            syncAngelOneHoldings();
        });

        sheet.show();
    }

    // --- MANUAL STOCK ENTRY ---

    private void showManualEntryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_stock, null);

        EditText inputTicker = dialogView.findViewById(R.id.inputTicker);
        EditText inputQuantity = dialogView.findViewById(R.id.inputQuantity);
        EditText inputPrice = dialogView.findViewById(R.id.inputPrice);

        new AlertDialog.Builder(this)
                .setTitle("Add Stock Manually")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String ticker = inputTicker.getText().toString().toUpperCase().trim();
                    String qtyStr = inputQuantity.getText().toString().trim();
                    String priceStr = inputPrice.getText().toString().trim();

                    if (ticker.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
                        Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        double quantity = Double.parseDouble(qtyStr);
                        double price = Double.parseDouble(priceStr);

                        stockViewModel.buyStock(ticker, "N/A", quantity, price, "MANUAL");
                        Toast.makeText(this, ticker + " added!", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- PDF UPLOAD FLOW ---

    private void startPdfUploadFlow() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            pdfPickerLauncher.launch(Intent.createChooser(intent, "Select CAS Statement"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPdfPasswordDialog(Uri pdfUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pdf_password, null);
        EditText inputPassword = dialogView.findViewById(R.id.inputPdfPassword);

        new AlertDialog.Builder(this)
                .setTitle("PDF Password")
                .setMessage("CAS statements are usually protected with your PAN number.")
                .setView(dialogView)
                .setPositiveButton("Parse", (dialog, which) -> {
                    String password = inputPassword.getText().toString().trim();
                    parseCasPdf(pdfUri, password);
                })
                .setNegativeButton("No Password", (dialog, which) -> {
                    parseCasPdf(pdfUri, null);
                })
                .show();
    }

    private void parseCasPdf(Uri pdfUri, String password) {
        showSyncProgress(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(pdfUri);
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        showSyncProgress(false);
                        Toast.makeText(this, "Could not read the PDF file", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                List<Stock> parsedStocks = CasPdfParser.parse(inputStream, password);
                inputStream.close();

                if (parsedStocks.isEmpty()) {
                    runOnUiThread(() -> {
                        showSyncProgress(false);
                        Toast.makeText(this, "No stocks found in the PDF. Ensure it's a valid CAS statement.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // Save all parsed stocks to DB
                for (Stock stock : parsedStocks) {
                    stockViewModel.buyStock(
                            stock.getTicker(),
                            stock.getSymbolToken(),
                            stock.getQuantity(),
                            stock.getAverageBuyPrice(),
                            "CAS"
                    );
                }

                runOnUiThread(() -> {
                    showSyncProgress(false);
                    Toast.makeText(this,
                            parsedStocks.size() + " stocks imported from CAS!",
                            Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "PDF parsing failed", e);
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("password")) {
                    errorMsg = "Incorrect password. CAS PDFs are typically protected with your PAN.";
                } else {
                    errorMsg = "Failed to parse PDF: " + errorMsg;
                }
                String finalMsg = errorMsg;
                runOnUiThread(() -> {
                    showSyncProgress(false);
                    Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // --- CREDENTIALS DIALOG ---

    private void showCredentialsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_credentials, null);

        EditText inputApiKey = dialogView.findViewById(R.id.inputApiKey);
        EditText inputClientCode = dialogView.findViewById(R.id.inputClientCode);
        EditText inputPin = dialogView.findViewById(R.id.inputPin);
        EditText inputTotp = dialogView.findViewById(R.id.inputTotpSecret);

        // Pre-fill existing credentials
        if (credentialManager.isConfigured()) {
            inputApiKey.setText(credentialManager.getApiKey());
            inputClientCode.setText(credentialManager.getClientCode());
            inputPin.setText(credentialManager.getPin());
            inputTotp.setText(credentialManager.getTotpSecret());
        }

        new AlertDialog.Builder(this)
                .setTitle("Angel One Credentials")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String apiKey = inputApiKey.getText().toString().trim();
                    String clientCode = inputClientCode.getText().toString().trim();
                    String pin = inputPin.getText().toString().trim();
                    String totp = inputTotp.getText().toString().trim();

                    if (apiKey.isEmpty() || clientCode.isEmpty() || pin.isEmpty() || totp.isEmpty()) {
                        Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    credentialManager.save(apiKey, clientCode, pin, totp);
                    AngelOneHelper.invalidateToken();
                    Toast.makeText(this, "Credentials saved!", Toast.LENGTH_SHORT).show();

                    // Test authentication
                    AngelOneHelper helper = new AngelOneHelper(credentialManager);
                    helper.authenticate(new AngelOneHelper.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(PortfolioActivity.this,
                                    "Connected to Angel One!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(PortfolioActivity.this,
                                    "Auth test failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    credentialManager.clear();
                    AngelOneHelper.invalidateToken();
                    Toast.makeText(this, "Credentials cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // --- UTILITIES ---

    private void showSyncProgress(boolean show) {
        progressSync.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSync.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private String formatCurrency(double amount) {
        if (amount >= 10000000) { // 1 Crore
            return String.format(Locale.getDefault(), "\u20B9%.2f Cr", amount / 10000000);
        } else if (amount >= 100000) { // 1 Lakh
            return String.format(Locale.getDefault(), "\u20B9%.2f L", amount / 100000);
        }
        return String.format(Locale.getDefault(), "\u20B9%.2f", amount);
    }
}

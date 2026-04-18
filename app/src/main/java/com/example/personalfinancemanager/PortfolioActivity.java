package com.example.personalfinancemanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class PortfolioActivity extends AppCompatActivity {

    private static final String TAG = "PortfolioActivity";

    private StockViewModel stockViewModel;
    private StockAdapter adapter;

    // Injected via ServiceLocator (was: new'd directly)
    private CredentialManager credentialManager;
    private BrokerApi brokerApi;         // Angel One
    private BrokerApi upstoxBrokerApi;   // Upstox (OAuth2)

    private TextView tvTotalInvested, tvCurrentValue, tvTotalPnl, tvPnlPercent;
    private ProgressBar progressSync;
    private ImageButton btnSync, btnSettings;
    private View emptyState;

    private final ActivityResultLauncher<Intent> pdfPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri pdfUri = result.getData().getData();
                    if (pdfUri != null) {
                        showPdfPasswordDialog(pdfUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> upstoxLoginLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Login succeeded — now sync holdings
                    syncUpstoxHoldings();
                } else {
                    String error = result.getData() != null
                            ? result.getData().getStringExtra(UpstoxLoginActivity.EXTRA_ERROR)
                            : null;
                    if (error != null) {
                        toast(getString(R.string.error_upstox_auth, error));
                    }
                }
            });

    /**
     * Skip the Portfolio re-auth prompt when we just resumed after the system
     * stole focus for our own BiometricPrompt. Without this flag the prompt
     * fires, succeeds, onResume runs, we see ourselves as "un-authed" (the
     * prompt success callback runs AFTER onResume on some OEMs) and re-prompt
     * in a loop.
     */
    private boolean authInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_portfolio);

        ServiceLocator services = ServiceLocator.get(this);
        credentialManager = services.credentialManager();
        brokerApi = services.brokerApi();
        upstoxBrokerApi = services.upstoxBrokerApi();

        gatePortfolioEntry();

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

        RecyclerView recyclerView = findViewById(R.id.recyclerViewPortfolio);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StockAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnStockLongClickListener((stock, position) -> {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_remove_title, stock.getTicker()))
                    .setMessage(R.string.dialog_remove_message)
                    .setPositiveButton(R.string.action_remove, (d, w) ->
                            AppDatabase.databaseWriteExecutor.execute(() ->
                                    AppDatabase.getDatabase(this).stockDao().deleteStock(stock.getTicker())))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });

        stockViewModel = new ViewModelProvider(this).get(StockViewModel.class);
        stockViewModel.getAllStocks().observe(this, stocks -> {
            adapter.setStocks(stocks);
            emptyState.setVisibility(stocks == null || stocks.isEmpty() ? View.VISIBLE : View.GONE);

            double totalInvested = 0.0;
            if (stocks != null) {
                for (Stock s : stocks) {
                    totalInvested += (s.getQuantity() * s.getAverageBuyPrice());
                }
            }
            tvTotalInvested.setText(formatCurrency(totalInvested));

            if (brokerApi.isAuthenticated() && stocks != null && !stocks.isEmpty()) {
                fetchLivePrices(stocks, totalInvested);
            } else {
                tvCurrentValue.setText(formatCurrency(totalInvested));
                tvTotalPnl.setText(R.string.format_zero);
                tvPnlPercent.setText("(0.00%)");
            }
        });

        btnBack.setOnClickListener(v -> finish());
        btnSync.setOnClickListener(v -> syncBrokerHoldings());
        btnSettings.setOnClickListener(v -> showAccountDialog());
        fabAddStock.setOnClickListener(v -> showAddOptionsSheet());
    }

    // ==================== AUTH FLOW ====================

    private void promptOtpThenRun(Runnable onAuthenticated) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_otp, null);
        EditText inputOtp = dialogView.findViewById(R.id.inputOtp);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_otp_title)
                .setMessage(R.string.dialog_otp_message)
                .setView(dialogView)
                .setPositiveButton(R.string.action_verify, (dialog, which) -> {
                    String otp = inputOtp.getText().toString().trim();
                    if (otp.length() != 6) {
                        toast(R.string.error_invalid_otp);
                        return;
                    }
                    showSyncProgress(true);
                    brokerApi.authenticate(otp, new BrokerApi.AuthCallback() {
                        @Override
                        public void onSuccess() {
                            showSyncProgress(false);
                            onAuthenticated.run();
                        }
                        @Override
                        public void onFailure(String error) {
                            showSyncProgress(false);
                            toast(getString(R.string.error_login_failed, error));
                        }
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void ensureAuthThenRun(Runnable action) {
        if (!credentialManager.isConfigured()) {
            showLoginDialog(action);
            return;
        }
        if (brokerApi.isAuthenticated()) {
            action.run();
        } else {
            promptOtpThenRun(action);
        }
    }

    // ==================== LOGIN ====================

    private void showLoginDialog(Runnable onLoginComplete) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        EditText inputClientCode = dialogView.findViewById(R.id.inputClientCode);
        EditText inputPin = dialogView.findViewById(R.id.inputPin);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_login_title)
                .setMessage(R.string.dialog_login_message)
                .setView(dialogView)
                .setPositiveButton(R.string.action_continue, (dialog, which) -> {
                    String clientCode = inputClientCode.getText().toString().trim();
                    String pin = inputPin.getText().toString().trim();
                    if (clientCode.isEmpty() || pin.isEmpty()) {
                        toast(R.string.error_fields_required);
                        return;
                    }
                    credentialManager.save(clientCode, pin);
                    brokerApi.logout(); // force fresh OTP on next call
                    if (onLoginComplete != null) {
                        promptOtpThenRun(onLoginComplete);
                    } else {
                        promptOtpThenRun(() -> toast(R.string.toast_connected));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(R.string.action_help, (dialog, which) -> showSetup2faHelp())
                .show();
    }

    private void showSetup2faHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_2fa_title)
                .setMessage(R.string.dialog_2fa_message)
                .setPositiveButton(R.string.action_open_angelone, (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("https://www.angelbroking.com/"));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.action_got_it, null)
                .show();
    }

    // ==================== ACCOUNT SETTINGS ====================

    private void showAccountDialog() {
        if (credentialManager.isConfigured()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_account_title)
                    .setMessage(getString(R.string.dialog_account_message,
                            credentialManager.getClientCode()))
                    .setPositiveButton(R.string.action_logout, (d, w) -> confirmLogoutBroker())
                    .setNeutralButton(R.string.action_delete_data, (d, w) -> confirmDeleteAllData())
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            showLoginDialog(null);
        }
    }

    /**
     * Re-prompts for biometrics before showing raw holdings when app-lock is
     * enabled and the last authentication is older than the grace window.
     * Covers holdings with a full-screen opaque scrim until success; cancels
     * bounce back to MainActivity via {@link #finish()}.
     */
    private void gatePortfolioEntry() {
        View scrim = findViewById(R.id.reauthScrim);
        if (credentialManager == null || !credentialManager.isBiometricLockEnabled()) {
            if (scrim != null) scrim.setVisibility(View.GONE);
            return;
        }
        if (BiometricGate.hasRecentAuth(BiometricGate.DEFAULT_GRACE_MS)) {
            if (scrim != null) scrim.setVisibility(View.GONE);
            return;
        }
        if (authInFlight) return;
        authInFlight = true;
        if (scrim != null) scrim.setVisibility(View.VISIBLE);

        BiometricGate.require(this,
                getString(R.string.reauth_title),
                getString(R.string.reauth_subtitle_portfolio),
                () -> {
                    authInFlight = false;
                    scrim.setVisibility(View.GONE);
                },
                () -> {
                    // Cancel / error — exit rather than sit behind the scrim.
                    authInFlight = false;
                    finish();
                });
    }

    /**
     * Biometric-gated broker logout. A bystander with an unlocked phone
     * shouldn't be able to sever the broker link (and thereby disrupt any
     * live price alerts / net-worth tracking) without re-auth.
     */
    private void confirmLogoutBroker() {
        Runnable doLogout = () -> {
            brokerApi.logout();
            credentialManager.clear();
            toast(R.string.toast_logged_out);
        };
        if (credentialManager != null && credentialManager.isBiometricLockEnabled()) {
            BiometricGate.require(this,
                    getString(R.string.reauth_title),
                    getString(R.string.reauth_subtitle_logout),
                    doLogout::run);
        } else {
            doLogout.run();
        }
    }

    /**
     * Required by Google Play account-deletion policy: users must be able to
     * remove all locally stored personal data (transactions, holdings,
     * credentials) without uninstalling the app.
     */
    private void confirmDeleteAllData() {
        // Re-auth gate — delete-all-data is irreversible. Require biometric
        // confirmation when app lock is on to prevent a bystander with an
        // unlocked phone from wiping user data.
        if (credentialManager != null && credentialManager.isBiometricLockEnabled()) {
            BiometricGate.require(this,
                    getString(R.string.reauth_title),
                    getString(R.string.reauth_subtitle),
                    this::showDeleteAllDataDialog);
        } else {
            showDeleteAllDataDialog();
        }
    }

    private void showDeleteAllDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_data_title)
                .setMessage(R.string.dialog_delete_data_message)
                .setPositiveButton(R.string.action_delete_everything, (d, w) -> {
                    brokerApi.logout();
                    credentialManager.clear();
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        AppDatabase db = AppDatabase.getDatabase(this);
                        db.transactionDao().deleteAllTransactions();
                        db.stockDao().deleteAll();
                        db.netWorthSnapshotDao().deleteAll();
                        db.priceAlertDao().deleteAll();
                        runOnUiThread(() -> {
                            toast(R.string.toast_data_deleted);
                            finish();
                        });
                    });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ==================== SYNC HOLDINGS ====================

    private void syncBrokerHoldings() {
        boolean angelConfigured = credentialManager.isConfigured();
        boolean upstoxConnected = upstoxBrokerApi.isAuthenticated();

        // If exactly one broker is set up, skip the chooser and go straight to sync.
        if (angelConfigured && !upstoxConnected) {
            syncAngelOneHoldings();
            return;
        }
        if (upstoxConnected && !angelConfigured) {
            syncUpstoxHoldings();
            return;
        }

        // Both connected → offer individual + "Sync Both" option.
        // Neither connected → offer setup options.
        // NOTE: AlertDialog.setMessage() and setItems() are mutually exclusive —
        // setMessage() hides the list. Use setTitle() only for the header text.
        if (angelConfigured) {
            // Both linked — show 3 options including "Sync Both"
            CharSequence[] options = new CharSequence[]{
                    getString(R.string.broker_angel_one),
                    getString(R.string.broker_upstox),
                    getString(R.string.broker_sync_both)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_choose_broker_message)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) syncAngelOneHoldings();
                        else if (which == 1) syncUpstoxHoldings();
                        else { syncAngelOneHoldings(); syncUpstoxHoldings(); }
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            // Neither linked — setup chooser
            CharSequence[] options = new CharSequence[]{
                    getString(R.string.broker_angel_one),
                    getString(R.string.broker_upstox)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_choose_broker_setup_message)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) syncAngelOneHoldings();
                        else syncUpstoxHoldings();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        }
    }

    private void syncAngelOneHoldings() {
        ensureAuthThenRun(() -> {
            showSyncProgress(true);
            toast(R.string.toast_syncing);
            brokerApi.fetchHoldings(new BrokerApi.HoldingsCallback() {
                @Override
                public void onHoldingsFetched(List<Stock> holdings) {
                    showSyncProgress(false);
                    if (holdings.isEmpty()) {
                        toast(R.string.toast_no_holdings);
                        return;
                    }
                    for (Stock stock : holdings) {
                        stockViewModel.buyStock(
                                stock.getTicker(), stock.getSymbolToken(),
                                stock.getQuantity(), stock.getAverageBuyPrice(), "AngelOne");
                    }
                    toast(getString(R.string.toast_synced_count, holdings.size()));
                }
                @Override
                public void onError(String error) {
                    showSyncProgress(false);
                    toast(getString(R.string.error_sync_failed, error));
                }
            });
        });
    }

    private void syncUpstoxHoldings() {
        if (!upstoxBrokerApi.isAuthenticated()) {
            launchUpstoxOAuth();
            return;
        }
        showSyncProgress(true);
        toast(R.string.toast_syncing);
        upstoxBrokerApi.fetchHoldings(new BrokerApi.HoldingsCallback() {
            @Override
            public void onHoldingsFetched(List<Stock> holdings) {
                showSyncProgress(false);
                if (holdings.isEmpty()) {
                    toast(R.string.toast_no_holdings);
                    return;
                }
                for (Stock stock : holdings) {
                    stockViewModel.buyStock(
                            stock.getTicker(), stock.getSymbolToken(),
                            stock.getQuantity(), stock.getAverageBuyPrice(), "Upstox");
                }
                toast(getString(R.string.toast_synced_count, holdings.size()));
            }
            @Override
            public void onError(String error) {
                showSyncProgress(false);
                toast(getString(R.string.error_sync_failed, error));
            }
        });
    }

    private void launchUpstoxOAuth() {
        if (BuildConfig.UPSTOX_CLIENT_ID.isEmpty()) {
            toast(R.string.error_upstox_not_configured);
            return;
        }
        upstoxLoginLauncher.launch(new Intent(this, UpstoxLoginActivity.class));
    }

    // ==================== LIVE PRICES ====================

    private void fetchLivePrices(List<Stock> stocks, double totalInvested) {
        showSyncProgress(true);
        brokerApi.fetchBatchLtp(stocks, priceMap -> {
            showSyncProgress(false);
            adapter.setPrices(priceMap);
            updatePnlSummary(totalInvested);
        });
    }

    private void updatePnlSummary(double totalInvested) {
        double currentValue = adapter.getTotalCurrentValue();
        double pnl = currentValue - totalInvested;
        double pnlPercent = totalInvested > 0 ? (pnl / totalInvested) * 100 : 0;

        tvCurrentValue.setText(formatCurrency(currentValue));
        tvTotalPnl.setText(String.format(Locale.getDefault(), "%s\u20B9%.2f",
                pnl >= 0 ? "+" : "", pnl));
        tvPnlPercent.setText(String.format(Locale.getDefault(), "(%.2f%%)", pnlPercent));

        int color = pnl >= 0 ? getColor(R.color.accent_green) : getColor(R.color.accent_red);
        tvTotalPnl.setTextColor(color);
        tvPnlPercent.setTextColor(color);
        tvCurrentValue.setTextColor(color);
    }

    // ==================== ADD OPTIONS ====================

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
            syncBrokerHoldings();
        });
        sheet.show();
    }

    private void showManualEntryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_stock, null);
        EditText inputTicker = dialogView.findViewById(R.id.inputTicker);
        EditText inputQuantity = dialogView.findViewById(R.id.inputQuantity);
        EditText inputPrice = dialogView.findViewById(R.id.inputPrice);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_manual_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_add, (dialog, which) -> {
                    String ticker = inputTicker.getText().toString().toUpperCase().trim();
                    String qtyStr = inputQuantity.getText().toString().trim();
                    String priceStr = inputPrice.getText().toString().trim();
                    if (ticker.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
                        toast(R.string.error_fill_all_fields);
                        return;
                    }
                    try {
                        double quantity = Double.parseDouble(qtyStr);
                        double price = Double.parseDouble(priceStr);
                        if (quantity <= 0 || price <= 0) {
                            toast(R.string.error_positive_numbers);
                            return;
                        }
                        stockViewModel.buyStock(ticker, "N/A", quantity, price, "MANUAL");
                        toast(getString(R.string.toast_stock_added, ticker));
                    } catch (NumberFormatException e) {
                        toast(R.string.error_invalid_number);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ==================== PDF UPLOAD ====================

    private void startPdfUploadFlow() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            pdfPickerLauncher.launch(Intent.createChooser(intent, getString(R.string.label_select_cas)));
        } catch (android.content.ActivityNotFoundException ex) {
            toast(R.string.error_no_file_manager);
        }
    }

    private void showPdfPasswordDialog(Uri pdfUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pdf_password, null);
        EditText inputPassword = dialogView.findViewById(R.id.inputPdfPassword);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_pdf_password_title)
                .setMessage(R.string.dialog_pdf_password_message)
                .setView(dialogView)
                .setPositiveButton(R.string.action_parse, (dialog, which) -> {
                    String password = inputPassword.getText().toString().trim();
                    parseCasPdf(pdfUri, password);
                })
                .setNegativeButton(R.string.action_no_password, (dialog, which) -> parseCasPdf(pdfUri, null))
                .show();
    }

    private void parseCasPdf(Uri pdfUri, String password) {
        showSyncProgress(true);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(pdfUri)) {
                if (inputStream == null) {
                    runOnUiThread(() -> {
                        showSyncProgress(false);
                        toast(R.string.error_pdf_unreadable);
                    });
                    return;
                }
                List<Stock> parsedStocks = CasPdfParser.parse(inputStream, password);
                if (parsedStocks.isEmpty()) {
                    runOnUiThread(() -> {
                        showSyncProgress(false);
                        toast(R.string.error_pdf_no_stocks);
                    });
                    return;
                }
                for (Stock stock : parsedStocks) {
                    stockViewModel.buyStock(
                            stock.getTicker(), stock.getSymbolToken(),
                            stock.getQuantity(), stock.getAverageBuyPrice(), "CAS");
                }
                runOnUiThread(() -> {
                    showSyncProgress(false);
                    toast(getString(R.string.toast_cas_imported, parsedStocks.size()));
                });
            } catch (Exception e) {
                Log.e(TAG, "PDF parsing failed", e);
                String msg = e.getMessage();
                final String finalMsg = (msg != null && msg.contains("password"))
                        ? getString(R.string.error_pdf_wrong_password)
                        : getString(R.string.error_pdf_failed, msg);
                runOnUiThread(() -> {
                    showSyncProgress(false);
                    toast(finalMsg);
                });
            }
        });
    }

    // ==================== UTILITIES ====================

    private void showSyncProgress(boolean show) {
        progressSync.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSync.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private String formatCurrency(double amount) {
        if (amount >= 10000000) {
            return String.format(Locale.getDefault(), "\u20B9%.2f Cr", amount / 10000000);
        } else if (amount >= 100000) {
            return String.format(Locale.getDefault(), "\u20B9%.2f L", amount / 100000);
        }
        return String.format(Locale.getDefault(), "\u20B9%.2f", amount);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

package com.example.personalfinancemanager;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AngelOneHelper {

    private static final String TAG = "AngelOne";
    private static final String BASE_URL = "https://apiconnect.angelbroking.com";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private static String jwtToken = "";
    private static long tokenTimestamp = 0;
    private static final long TOKEN_VALIDITY_MS = 23 * 60 * 60 * 1000L; // 23 hours

    // Credentials loaded from CredentialManager
    private final String apiKey;
    private final String clientCode;
    private final String pin;
    private final String totpSecret;

    public interface StockPriceCallback {
        void onPriceFetched(double livePrice);
    }

    public interface HoldingsCallback {
        void onHoldingsFetched(List<Stock> myStocks);
    }

    public interface BatchLtpCallback {
        void onPricesFetched(Map<String, Double> priceMap);
    }

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public AngelOneHelper(CredentialManager credentialManager) {
        this.apiKey = credentialManager.getApiKey();
        this.clientCode = credentialManager.getClientCode();
        this.pin = credentialManager.getPin();
        this.totpSecret = credentialManager.getTotpSecret();
    }

    private boolean isTokenValid() {
        return !jwtToken.isEmpty()
                && (System.currentTimeMillis() - tokenTimestamp) < TOKEN_VALIDITY_MS;
    }

    private Request.Builder addStandardHeaders(Request.Builder builder) {
        return builder
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-UserType", "USER")
                .addHeader("X-SourceID", "WEB")
                .addHeader("X-ClientLocalIP", "127.0.0.1")
                .addHeader("X-ClientPublicIP", "127.0.0.1")
                .addHeader("X-MACAddress", "00:00:00:00:00:00")
                .addHeader("X-PrivateKey", apiKey);
    }

    // --- AUTHENTICATION ---

    public void authenticate(AuthCallback callback) {
        if (isTokenValid()) {
            callback.onSuccess();
            return;
        }

        // Force re-auth
        jwtToken = "";

        try {
            DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
            long currentBucket = System.currentTimeMillis() / 1000 / 30;
            String currentTotp = codeGenerator.generate(totpSecret, currentBucket);

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("clientcode", clientCode);
            bodyJson.put("password", pin);
            bodyJson.put("totp", currentTotp);

            RequestBody body = RequestBody.create(
                    bodyJson.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = addStandardHeaders(new Request.Builder())
                    .url(BASE_URL + "/rest/auth/angelbroking/user/v1/loginByPassword")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Auth network failure", e);
                    postToMain(() -> callback.onFailure("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful() || response.body() == null) {
                            postToMain(() -> callback.onFailure("Auth failed: HTTP " + response.code()));
                            return;
                        }
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getBoolean("status")) {
                            jwtToken = json.getJSONObject("data").getString("jwtToken");
                            tokenTimestamp = System.currentTimeMillis();
                            postToMain(callback::onSuccess);
                        } else {
                            String msg = json.optString("message", "Authentication failed");
                            postToMain(() -> callback.onFailure(msg));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Auth parse error", e);
                        postToMain(() -> callback.onFailure("Parse error: " + e.getMessage()));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TOTP generation failed", e);
            callback.onFailure("TOTP error: " + e.getMessage());
        }
    }

    // --- FETCH HOLDINGS ---

    public void fetchMyHoldings(HoldingsCallback callback) {
        authenticate(new AuthCallback() {
            @Override
            public void onSuccess() {
                Request request = addStandardHeaders(new Request.Builder())
                        .url(BASE_URL + "/rest/secure/angelbroking/portfolio/v1/getHolding")
                        .addHeader("Authorization", "Bearer " + jwtToken)
                        .get()
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Holdings fetch failed", e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful() || response.body() == null) return;
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.getBoolean("status") && !json.isNull("data")) {
                                JSONArray array = json.getJSONArray("data");
                                List<Stock> portfolio = new ArrayList<>();

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    String symbol = item.getString("tradingsymbol").replace("-EQ", "");
                                    String token = item.getString("symboltoken");
                                    double qty = item.getDouble("quantity");
                                    double avgPrice = item.getDouble("averageprice");

                                    portfolio.add(new Stock(symbol, token, qty, avgPrice, "AngelOne"));
                                }
                                postToMain(() -> callback.onHoldingsFetched(portfolio));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Holdings parse error", e);
                        }
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Auth failed before holdings fetch: " + error);
            }
        });
    }

    // --- BATCH LTP FETCH (All stocks at once) ---

    public void fetchBatchLtp(List<Stock> stocks, BatchLtpCallback callback) {
        if (stocks == null || stocks.isEmpty()) {
            callback.onPricesFetched(new HashMap<>());
            return;
        }

        // Filter stocks that have a valid symbol token
        List<Stock> fetchable = new ArrayList<>();
        for (Stock s : stocks) {
            if (s.getSymbolToken() != null
                    && !s.getSymbolToken().isEmpty()
                    && !s.getSymbolToken().equals("N/A")) {
                fetchable.add(s);
            }
        }

        if (fetchable.isEmpty()) {
            callback.onPricesFetched(new HashMap<>());
            return;
        }

        authenticate(new AuthCallback() {
            @Override
            public void onSuccess() {
                Map<String, Double> priceMap = new HashMap<>();
                AtomicInteger remaining = new AtomicInteger(fetchable.size());

                for (Stock stock : fetchable) {
                    fetchSingleLtp(stock.getTicker(), stock.getSymbolToken(), price -> {
                        synchronized (priceMap) {
                            if (price > 0) {
                                priceMap.put(stock.getTicker(), price);
                            }
                        }
                        if (remaining.decrementAndGet() == 0) {
                            postToMain(() -> callback.onPricesFetched(priceMap));
                        }
                    });
                }
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Auth failed before batch LTP: " + error);
                postToMain(() -> callback.onPricesFetched(new HashMap<>()));
            }
        });
    }

    // --- SINGLE LTP FETCH (internal, no auth check) ---

    private void fetchSingleLtp(String ticker, String stockToken, StockPriceCallback callback) {
        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("exchange", "NSE");
            bodyJson.put("tradingsymbol", ticker + "-EQ");
            bodyJson.put("symboltoken", stockToken);

            RequestBody body = RequestBody.create(
                    bodyJson.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = addStandardHeaders(new Request.Builder())
                    .url(BASE_URL + "/rest/secure/angelbroking/order/v1/getLtpData")
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "LTP failed for " + ticker, e);
                    callback.onPriceFetched(0);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onPriceFetched(0);
                        return;
                    }
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        if (json.getBoolean("status")) {
                            double ltp = json.getJSONObject("data").getDouble("ltp");
                            callback.onPriceFetched(ltp);
                        } else {
                            callback.onPriceFetched(0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "LTP parse error for " + ticker, e);
                        callback.onPriceFetched(0);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "LTP request build error", e);
            callback.onPriceFetched(0);
        }
    }

    // --- PUBLIC SINGLE LTP (for on-demand refresh) ---

    public void loginAndFetchPrice(String ticker, String stockToken, StockPriceCallback callback) {
        authenticate(new AuthCallback() {
            @Override
            public void onSuccess() {
                fetchSingleLtp(ticker, stockToken, price ->
                        postToMain(() -> callback.onPriceFetched(price)));
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Auth failed: " + error);
            }
        });
    }

    public static void invalidateToken() {
        jwtToken = "";
        tokenTimestamp = 0;
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}

package com.example.personalfinancemanager;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
    private static final String API_KEY = "YGWfQ7oP";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private static String jwtToken = "";
    private static long tokenTimestamp = 0;
    private static final long TOKEN_VALIDITY_MS = 23 * 60 * 60 * 1000L;
    private static boolean tokenLoaded = false;

    private final String clientCode;
    private final String pin;
    private final CredentialManager credentialManager;

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
        this.credentialManager = credentialManager;
        this.clientCode = credentialManager.getClientCode();
        this.pin = credentialManager.getPin();

        // Load persisted token once per app session
        if (!tokenLoaded) {
            String savedToken = credentialManager.getToken();
            long savedTimestamp = credentialManager.getTokenTimestamp();
            if (!savedToken.isEmpty() && savedTimestamp > 0) {
                jwtToken = savedToken;
                tokenTimestamp = savedTimestamp;
            }
            tokenLoaded = true;
        }
    }

    public static boolean isTokenValid() {
        return !jwtToken.isEmpty()
                && (System.currentTimeMillis() - tokenTimestamp) < TOKEN_VALIDITY_MS;
    }

    /**
     * Call this from PortfolioActivity.onCreate to load persisted token
     * before any isTokenValid() checks.
     */
    public static void loadPersistedToken(CredentialManager cm) {
        if (!tokenLoaded) {
            String savedToken = cm.getToken();
            long savedTimestamp = cm.getTokenTimestamp();
            if (!savedToken.isEmpty() && savedTimestamp > 0) {
                jwtToken = savedToken;
                tokenTimestamp = savedTimestamp;
            }
            tokenLoaded = true;
        }
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
                .addHeader("X-PrivateKey", API_KEY);
    }

    // --- AUTHENTICATION (takes 6-digit OTP from Google Authenticator) ---

    public void authenticate(String otpCode, AuthCallback callback) {
        if (isTokenValid()) {
            callback.onSuccess();
            return;
        }

        jwtToken = "";

        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("clientcode", clientCode);
            bodyJson.put("password", pin);
            bodyJson.put("totp", otpCode);

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

                            // Persist token so it survives app restarts
                            credentialManager.saveToken(jwtToken, tokenTimestamp);

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
            Log.e(TAG, "Auth request failed", e);
            callback.onFailure("Request error: " + e.getMessage());
        }
    }

    // --- FETCH HOLDINGS (requires valid token) ---

    public void fetchMyHoldings(HoldingsCallback callback) {
        if (!isTokenValid()) {
            Log.e(TAG, "Cannot fetch holdings: not authenticated");
            return;
        }

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

    // --- BATCH LTP FETCH ---

    public void fetchBatchLtp(List<Stock> stocks, BatchLtpCallback callback) {
        if (stocks == null || stocks.isEmpty()) {
            callback.onPricesFetched(new HashMap<>());
            return;
        }

        if (!isTokenValid()) {
            callback.onPricesFetched(new HashMap<>());
            return;
        }

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

        Map<String, Double> priceMap = new HashMap<>();
        AtomicInteger remaining = new AtomicInteger(fetchable.size());

        for (Stock stock : fetchable) {
            fetchSingleLtp(stock.getTicker(), stock.getSymbolToken(), price -> {
                synchronized (priceMap) {
                    if (price > 0) priceMap.put(stock.getTicker(), price);
                }
                if (remaining.decrementAndGet() == 0) {
                    postToMain(() -> callback.onPricesFetched(priceMap));
                }
            });
        }
    }

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
                            callback.onPriceFetched(json.getJSONObject("data").getDouble("ltp"));
                        } else {
                            callback.onPriceFetched(0);
                        }
                    } catch (Exception e) {
                        callback.onPriceFetched(0);
                    }
                }
            });
        } catch (Exception e) {
            callback.onPriceFetched(0);
        }
    }

    public static void invalidateToken() {
        jwtToken = "";
        tokenTimestamp = 0;
        tokenLoaded = false;
    }

    public static void invalidateToken(CredentialManager cm) {
        invalidateToken();
        cm.clearToken();
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}

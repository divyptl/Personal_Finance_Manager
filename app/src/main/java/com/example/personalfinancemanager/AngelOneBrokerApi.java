package com.example.personalfinancemanager;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Concrete {@link BrokerApi} implementation for Angel One's Smart API.
 *
 * <p>This is the new home for what used to live in {@code AngelOneHelper}.
 * Differences from the old class:
 *   <ul>
 *     <li>No static state — token/credentials live in injected collaborators.</li>
 *     <li>API key & headers come from {@link StandardHeadersInterceptor},
 *         not concatenated by hand at every call site.</li>
 *     <li>Implements {@link BrokerApi} so {@code PortfolioActivity} doesn't
 *         depend on Angel One concretely.</li>
 *     <li>Reports holdings errors via {@link BrokerApi.HoldingsCallback#onError}
 *         instead of silently logging — the previous code swallowed every
 *         failure, leaving users staring at an empty list with no feedback.</li>
 *   </ul>
 */
public class AngelOneBrokerApi implements BrokerApi {

    private static final String TAG = "AngelOneBrokerApi";
    private static final String BASE_URL = "https://apiconnect.angelbroking.com";

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final CredentialManager credentialManager;
    private final AngelOneTokenManager tokenManager;
    private final OkHttpClient httpClient;

    public AngelOneBrokerApi(CredentialManager credentialManager,
                             AngelOneTokenManager tokenManager,
                             OkHttpClient httpClient) {
        this.credentialManager = credentialManager;
        this.tokenManager = tokenManager;
        this.httpClient = httpClient;
    }

    @Override
    public boolean isAuthenticated() {
        return tokenManager.isValid();
    }

    @Override
    public void authenticate(String totpCode, AuthCallback callback) {
        if (tokenManager.isValid()) {
            callback.onSuccess();
            return;
        }
        if (!credentialManager.isConfigured()) {
            callback.onFailure("Angel One credentials not set up");
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("clientcode", credentialManager.getClientCode());
            body.put("password", credentialManager.getPin());
            body.put("totp", totpCode);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/rest/auth/angelbroking/user/v1/loginByPassword")
                    .post(RequestBody.create(body.toString(), JSON_MEDIA))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Auth network failure", e);
                    postToMain(() -> callback.onFailure("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response r = response) {
                        if (!r.isSuccessful() || r.body() == null) {
                            postToMain(() -> callback.onFailure("Auth failed: HTTP " + r.code()));
                            return;
                        }
                        JSONObject json = new JSONObject(r.body().string());
                        if (json.optBoolean("status", false)) {
                            String jwt = json.getJSONObject("data").getString("jwtToken");
                            tokenManager.update(jwt);
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

    @Override
    public void fetchHoldings(HoldingsCallback callback) {
        if (!tokenManager.isValid()) {
            postToMain(() -> callback.onError("Not authenticated"));
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_URL + "/rest/secure/angelbroking/portfolio/v1/getHolding")
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Holdings fetch failed", e);
                postToMain(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        postToMain(() -> callback.onError("HTTP " + r.code()));
                        return;
                    }
                    JSONObject json = new JSONObject(r.body().string());
                    if (!json.optBoolean("status", false) || json.isNull("data")) {
                        postToMain(() -> callback.onError(
                                json.optString("message", "Empty response")));
                        return;
                    }
                    JSONArray array = json.getJSONArray("data");
                    List<Stock> portfolio = new ArrayList<>(array.length());
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.getJSONObject(i);
                        String symbol = item.getString("tradingsymbol").replace("-EQ", "");
                        String token  = item.getString("symboltoken");
                        double qty    = item.getDouble("quantity");
                        double avgPrice = item.getDouble("averageprice");
                        portfolio.add(new Stock(symbol, token, qty, avgPrice, "AngelOne"));
                    }
                    postToMain(() -> callback.onHoldingsFetched(portfolio));
                } catch (Exception e) {
                    Log.e(TAG, "Holdings parse error", e);
                    postToMain(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    @Override
    public void fetchBatchLtp(List<Stock> stocks, BatchLtpCallback callback) {
        if (stocks == null || stocks.isEmpty() || !tokenManager.isValid()) {
            postToMain(() -> callback.onPricesFetched(new HashMap<>()));
            return;
        }

        List<Stock> fetchable = new ArrayList<>();
        for (Stock s : stocks) {
            String tk = s.getSymbolToken();
            if (tk != null && !tk.isEmpty() && !tk.equals("N/A")) {
                fetchable.add(s);
            }
        }
        if (fetchable.isEmpty()) {
            postToMain(() -> callback.onPricesFetched(new HashMap<>()));
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

    private interface PriceConsumer { void onPrice(double price); }

    private void fetchSingleLtp(String ticker, String stockToken, PriceConsumer consumer) {
        try {
            JSONObject body = new JSONObject();
            body.put("exchange", "NSE");
            body.put("tradingsymbol", ticker + "-EQ");
            body.put("symboltoken", stockToken);

            Request request = new Request.Builder()
                    .url(BASE_URL + "/rest/secure/angelbroking/order/v1/getLtpData")
                    .header("Authorization", "Bearer " + tokenManager.getToken())
                    .post(RequestBody.create(body.toString(), JSON_MEDIA))
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    consumer.onPrice(0);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (Response r = response) {
                        if (!r.isSuccessful() || r.body() == null) { consumer.onPrice(0); return; }
                        JSONObject json = new JSONObject(r.body().string());
                        if (json.optBoolean("status", false)) {
                            consumer.onPrice(json.getJSONObject("data").getDouble("ltp"));
                        } else {
                            consumer.onPrice(0);
                        }
                    } catch (Exception e) {
                        consumer.onPrice(0);
                    }
                }
            });
        } catch (Exception e) {
            consumer.onPrice(0);
        }
    }

    @Override
    public void logout() {
        tokenManager.invalidate();
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}

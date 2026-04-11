package com.example.personalfinancemanager;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Concrete {@link BrokerApi} implementation for Upstox's v2 REST API.
 *
 * <p>Unlike Angel One (username + PIN + TOTP), Upstox uses OAuth2
 * authorization-code flow:
 *   <ol>
 *     <li>App opens {@link #buildAuthorizationUrl()} in a Custom Tab.</li>
 *     <li>User logs in at Upstox's domain.</li>
 *     <li>Upstox redirects to our deep link with ?code=XYZ.</li>
 *     <li>{@link UpstoxOAuthCallbackActivity} captures the code and calls
 *         {@link #completeOAuthLogin(String, AuthCallback)} which POSTs the
 *         code to the token endpoint and stores the resulting bearer token.</li>
 *   </ol>
 *
 * <p>The {@link #authenticate(String, AuthCallback)} entry point is a no-op
 * for Upstox — it exists only to satisfy the broker-agnostic interface that
 * Angel One needs for TOTP. Callers should check {@link #usesOAuthLogin()}
 * and route through {@link #buildAuthorizationUrl()} instead.
 */
public class UpstoxBrokerApi implements BrokerApi {

    private static final String TAG = "UpstoxBrokerApi";

    private static final String BASE_URL   = "https://api.upstox.com";
    private static final String AUTH_URL   = BASE_URL + "/v2/login/authorization/dialog";
    private static final String TOKEN_URL  = BASE_URL + "/v2/login/authorization/token";
    private static final String HOLDINGS_URL = BASE_URL + "/v2/portfolio/long-term-holdings";
    private static final String LTP_URL    = BASE_URL + "/v2/market-quote/ltp";

    private final UpstoxTokenManager tokenManager;
    private final OkHttpClient httpClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public UpstoxBrokerApi(UpstoxTokenManager tokenManager,
                           OkHttpClient httpClient,
                           String clientId,
                           String clientSecret,
                           String redirectUri) {
        this.tokenManager = tokenManager;
        this.httpClient = httpClient;
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.redirectUri = redirectUri == null ? "" : redirectUri;
    }

    @Override
    public String brokerId() {
        return "Upstox";
    }

    @Override
    public boolean usesOAuthLogin() {
        return true;
    }

    @Override
    public boolean isAuthenticated() {
        return tokenManager.isValid();
    }

    /** Unsupported for OAuth brokers — provided only to satisfy the interface. */
    @Override
    public void authenticate(String totpCode, AuthCallback callback) {
        if (tokenManager.isValid()) {
            callback.onSuccess();
            return;
        }
        callback.onFailure("Upstox uses OAuth — start the browser login flow instead");
    }

    @Nullable
    @Override
    public String buildAuthorizationUrl() {
        if (clientId.isEmpty() || redirectUri.isEmpty()) {
            Log.w(TAG, "Upstox client_id / redirect_uri not configured in local.properties");
            return null;
        }
        return Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .build()
                .toString();
    }

    @Override
    public void completeOAuthLogin(String authorizationCode, AuthCallback callback) {
        if (clientId.isEmpty() || clientSecret.isEmpty() || redirectUri.isEmpty()) {
            postToMain(() -> callback.onFailure("Upstox credentials not configured"));
            return;
        }
        if (authorizationCode == null || authorizationCode.isEmpty()) {
            postToMain(() -> callback.onFailure("Missing authorization code"));
            return;
        }

        FormBody body = new FormBody.Builder()
                .add("code", authorizationCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .header("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "OAuth token exchange failed", e);
                postToMain(() -> callback.onFailure("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    if (r.body() == null) {
                        postToMain(() -> callback.onFailure("Empty token response"));
                        return;
                    }
                    JSONObject json = new JSONObject(r.body().string());
                    if (!r.isSuccessful()) {
                        String err = json.optString("error_description",
                                json.optString("error", "HTTP " + r.code()));
                        postToMain(() -> callback.onFailure("Upstox auth failed: " + err));
                        return;
                    }
                    String accessToken = json.optString("access_token", "");
                    if (accessToken.isEmpty()) {
                        postToMain(() -> callback.onFailure("No access_token in response"));
                        return;
                    }
                    tokenManager.update(accessToken);
                    postToMain(callback::onSuccess);
                } catch (Exception e) {
                    Log.e(TAG, "OAuth parse error", e);
                    postToMain(() -> callback.onFailure("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    @Override
    public void fetchHoldings(HoldingsCallback callback) {
        if (!tokenManager.isValid()) {
            postToMain(() -> callback.onError("Not authenticated with Upstox"));
            return;
        }

        Request request = new Request.Builder()
                .url(HOLDINGS_URL)
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenManager.getToken())
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
                    if (!"success".equalsIgnoreCase(json.optString("status"))) {
                        postToMain(() -> callback.onError(
                                json.optString("message", "Upstox returned non-success")));
                        return;
                    }
                    JSONArray data = json.optJSONArray("data");
                    if (data == null) {
                        postToMain(() -> callback.onHoldingsFetched(new ArrayList<>()));
                        return;
                    }
                    List<Stock> out = new ArrayList<>(data.length());
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        // Upstox holding payload uses tradingsymbol + instrument_token
                        // (the latter is the opaque "NSE_EQ|INE..." instrument key).
                        String symbol = item.optString("tradingsymbol",
                                item.optString("trading_symbol", ""));
                        String instrumentKey = item.optString("instrument_token",
                                item.optString("instrument_key", ""));
                        double qty = item.optDouble("quantity", 0);
                        double avg = item.optDouble("average_price",
                                item.optDouble("avg_price", 0));
                        if (symbol.isEmpty() || qty <= 0) continue;
                        out.add(new Stock(symbol, instrumentKey, qty, avg, "Upstox"));
                    }
                    postToMain(() -> callback.onHoldingsFetched(out));
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

        // Upstox supports batching via comma-separated instrument_key query param.
        StringBuilder keys = new StringBuilder();
        Map<String, Stock> keyToStock = new HashMap<>();
        for (Stock s : stocks) {
            String tk = s.getSymbolToken();
            if (tk == null || tk.isEmpty() || tk.equals("N/A")) continue;
            if (keys.length() > 0) keys.append(',');
            keys.append(tk);
            keyToStock.put(tk, s);
        }
        if (keys.length() == 0) {
            postToMain(() -> callback.onPricesFetched(new HashMap<>()));
            return;
        }

        HttpUrl url = HttpUrl.parse(LTP_URL).newBuilder()
                .addQueryParameter("instrument_key", keys.toString())
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenManager.getToken())
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "LTP fetch failed", e);
                postToMain(() -> callback.onPricesFetched(new HashMap<>()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    Map<String, Double> priceMap = new HashMap<>();
                    if (!r.isSuccessful() || r.body() == null) {
                        postToMain(() -> callback.onPricesFetched(priceMap));
                        return;
                    }
                    JSONObject json = new JSONObject(r.body().string());
                    JSONObject data = json.optJSONObject("data");
                    if (data == null) {
                        postToMain(() -> callback.onPricesFetched(priceMap));
                        return;
                    }
                    Iterator<String> it = data.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject entry = data.optJSONObject(k);
                        if (entry == null) continue;
                        double ltp = entry.optDouble("last_price", 0);
                        String instrumentKey = entry.optString("instrument_token", k);
                        Stock s = keyToStock.get(instrumentKey);
                        if (s == null) {
                            // Upstox returns map keys as "NSE_EQ:SYMBOL"; fall back to matching by ticker.
                            for (Stock candidate : keyToStock.values()) {
                                if (k.endsWith(candidate.getTicker())) {
                                    s = candidate;
                                    break;
                                }
                            }
                        }
                        if (s != null && ltp > 0) {
                            priceMap.put(s.getTicker(), ltp);
                        }
                    }
                    postToMain(() -> callback.onPricesFetched(priceMap));
                } catch (Exception e) {
                    Log.e(TAG, "LTP parse error", e);
                    postToMain(() -> callback.onPricesFetched(new HashMap<>()));
                }
            }
        });
    }

    @Override
    public void logout() {
        tokenManager.invalidate();
    }

    private static void postToMain(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}

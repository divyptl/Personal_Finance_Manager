package com.example.personalfinancemanager; // REPLACE WITH YOUR PACKAGE NAME

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
import java.util.List;

public class AngelOneHelper {

    // 🔴 PASTE YOUR EXACT CREDENTIALS HERE 🔴
    private static final String API_KEY = "YGWfQ7oP";
    private static final String CLIENT_CODE = "D60818456";
    private static final String PIN = "9690";
    private static final String TOTP_SECRET = "Y2HVSP2BBV7K5PI5LRQ67MGHRY";

    private static final OkHttpClient client = new OkHttpClient();
    private static String jwtToken = "";

    public interface StockPriceCallback { void onPriceFetched(double livePrice); }
    public interface HoldingsCallback { void onHoldingsFetched(List<Stock> myStocks); }

    // 1. THE CORE LOGIN ENGINE
    private static void authenticate(Runnable onSuccess) {
        if (!jwtToken.isEmpty()) {
            onSuccess.run();
            return;
        }
        try {
            DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
            long currentBucket = System.currentTimeMillis() / 1000 / 30;
            String currentTotp = codeGenerator.generate(TOTP_SECRET, currentBucket);

            String jsonBody = "{\"clientcode\":\"" + CLIENT_CODE + "\",\"password\":\"" + PIN + "\",\"totp\":\"" + currentTotp + "\"}";
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url("https://apiconnect.angelbroking.com/rest/auth/angelbroking/user/v1/loginByPassword")
                    .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                    .addHeader("X-UserType", "USER").addHeader("X-SourceID", "WEB")
                    .addHeader("X-ClientLocalIP", "192.168.1.1").addHeader("X-ClientPublicIP", "106.193.147.98")
                    .addHeader("X-MACAddress", "00-B0-D0-63-C2-26").addHeader("X-PrivateKey", API_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { Log.e("AngelOne", "Auth Failed", e); }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.getBoolean("status")) {
                                jwtToken = json.getJSONObject("data").getString("jwtToken");
                                onSuccess.run();
                            }
                        } catch (Exception e) { Log.e("AngelOne", "Auth Parse Error", e); }
                    }
                }
            });
        } catch (Exception e) { Log.e("AngelOne", "TOTP Failed", e); }
    }

    // 2. THE HOLDINGS ENGINE (Downloads your real portfolio via GET request)
    public static void fetchMyHoldings(HoldingsCallback callback) {
        authenticate(() -> {
            Request request = new Request.Builder()
                    .url("https://apiconnect.angelbroking.com/rest/secure/angelbroking/portfolio/v1/getHolding")
                    .get()
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                    .addHeader("X-UserType", "USER").addHeader("X-SourceID", "WEB")
                    .addHeader("X-ClientLocalIP", "192.168.1.1").addHeader("X-ClientPublicIP", "106.193.147.98")
                    .addHeader("X-MACAddress", "00-B0-D0-63-C2-26").addHeader("X-PrivateKey", API_KEY)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { Log.e("AngelOne", "Holdings Failed", e); }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.getBoolean("status") && !json.isNull("data")) {
                                JSONArray array = json.getJSONArray("data");
                                List<Stock> portfolio = new ArrayList<>();

                                for (int i = 0; i < array.length(); i++) {
                                    JSONObject item = array.getJSONObject(i);
                                    String symbol = item.getString("tradingsymbol").replace("-EQ", "");
                                    String token = item.getString("symboltoken"); // GRAB THE TOKEN!
                                    double qty = item.getDouble("quantity");
                                    double avgPrice = item.getDouble("averageprice");

                                    portfolio.add(new Stock(symbol, token, qty, avgPrice));
                                }
                                new Handler(Looper.getMainLooper()).post(() -> callback.onHoldingsFetched(portfolio));
                            }
                        } catch (Exception e) { Log.e("AngelOne", "Holdings Parse Error", e); }
                    }
                }
            });
        });
    }

    // 3. THE LIVE PRICE ENGINE (Called by the StockAdapter)
        // Accept the token as a requirement
        public static void loginAndFetchPrice(String ticker, String stockToken, StockPriceCallback callback) {
            authenticate(() -> {
                String jsonBody = "{\"exchange\":\"NSE\",\"tradingsymbol\":\"" + ticker + "-EQ\",\"symboltoken\":\"" + stockToken + "\"}";

                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));


            Request request = new Request.Builder()
                    .url("https://apiconnect.angelbroking.com/rest/secure/angelbroking/order/v1/getLtpData")
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                    .addHeader("X-UserType", "USER").addHeader("X-SourceID", "WEB")
                    .addHeader("X-ClientLocalIP", "192.168.1.1").addHeader("X-ClientPublicIP", "106.193.147.98")
                    .addHeader("X-MACAddress", "00-B0-D0-63-C2-26").addHeader("X-PrivateKey", API_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) { Log.e("AngelOne", "LTP Failed", e); }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.getBoolean("status")) {
                                final double livePrice = json.getJSONObject("data").getDouble("ltp");
                                new Handler(Looper.getMainLooper()).post(() -> callback.onPriceFetched(livePrice));
                            }
                        } catch (Exception e) { Log.e("AngelOne", "LTP Parse Error", e); }
                    }
                }
            });
        });
    }
}
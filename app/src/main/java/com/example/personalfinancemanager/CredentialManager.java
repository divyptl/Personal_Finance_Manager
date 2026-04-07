package com.example.personalfinancemanager;

import android.content.Context;
import android.content.SharedPreferences;

public class CredentialManager {

    private static final String PREFS_NAME = "angel_one_credentials";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_CLIENT_CODE = "client_code";
    private static final String KEY_PIN = "pin";
    private static final String KEY_TOTP_SECRET = "totp_secret";

    private final SharedPreferences prefs;

    public CredentialManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(String apiKey, String clientCode, String pin, String totpSecret) {
        prefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_CLIENT_CODE, clientCode)
                .putString(KEY_PIN, pin)
                .putString(KEY_TOTP_SECRET, totpSecret)
                .apply();
    }

    public String getApiKey() { return prefs.getString(KEY_API_KEY, ""); }
    public String getClientCode() { return prefs.getString(KEY_CLIENT_CODE, ""); }
    public String getPin() { return prefs.getString(KEY_PIN, ""); }
    public String getTotpSecret() { return prefs.getString(KEY_TOTP_SECRET, ""); }

    public boolean isConfigured() {
        return !getApiKey().isEmpty()
                && !getClientCode().isEmpty()
                && !getPin().isEmpty()
                && !getTotpSecret().isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}

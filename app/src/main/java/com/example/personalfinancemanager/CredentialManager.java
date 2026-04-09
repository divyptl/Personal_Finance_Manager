package com.example.personalfinancemanager;

import android.content.Context;
import android.content.SharedPreferences;

public class CredentialManager {

    private static final String PREFS_NAME = "angel_one_credentials";
    private static final String KEY_CLIENT_CODE = "client_code";
    private static final String KEY_PIN = "pin";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_TOKEN_TIMESTAMP = "token_timestamp";

    private final SharedPreferences prefs;

    public CredentialManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(String clientCode, String pin) {
        prefs.edit()
                .putString(KEY_CLIENT_CODE, clientCode)
                .putString(KEY_PIN, pin)
                .apply();
    }

    public String getClientCode() { return prefs.getString(KEY_CLIENT_CODE, ""); }
    public String getPin() { return prefs.getString(KEY_PIN, ""); }

    public boolean isConfigured() {
        return !getClientCode().isEmpty() && !getPin().isEmpty();
    }

    // --- JWT Token Persistence ---

    public void saveToken(String token, long timestamp) {
        prefs.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putLong(KEY_TOKEN_TIMESTAMP, timestamp)
                .apply();
    }

    public String getToken() { return prefs.getString(KEY_JWT_TOKEN, ""); }
    public long getTokenTimestamp() { return prefs.getLong(KEY_TOKEN_TIMESTAMP, 0); }

    public void clearToken() {
        prefs.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_TOKEN_TIMESTAMP)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}

package com.example.personalfinancemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Stores Angel One credentials and the JWT session token in
 * {@link EncryptedSharedPreferences}, backed by an AES-256 master key held in
 * the Android Keystore.
 *
 * <p>If EncryptedSharedPreferences fails to initialize (rare — happens on
 * devices with broken Keystore implementations) we fall back to plain
 * SharedPreferences and log a warning, so the app keeps working but operates
 * without at-rest encryption. Callers can detect this with
 * {@link #isUsingEncryption()} and surface a UX warning if desired.
 *
 * <p>The pref file name {@code wealthflow_secure_prefs} is also referenced from
 * {@code backup_rules.xml} to keep it out of cloud backups.
 */
public class CredentialManager {

    private static final String TAG = "CredentialManager";

    private static final String SECURE_PREFS_NAME = "wealthflow_secure_prefs";
    private static final String LEGACY_PREFS_NAME = "angel_one_credentials";

    private static final String KEY_CLIENT_CODE     = "client_code";
    private static final String KEY_PIN             = "pin";
    private static final String KEY_JWT_TOKEN       = "jwt_token";
    private static final String KEY_TOKEN_TIMESTAMP = "token_timestamp";

    // Upstox OAuth2 access token — separate namespace so Angel One logout
    // doesn't clear Upstox state and vice-versa.
    private static final String KEY_UPSTOX_TOKEN     = "upstox_access_token";
    private static final String KEY_UPSTOX_TIMESTAMP = "upstox_token_timestamp";

    private final SharedPreferences prefs;
    private final boolean encrypted;

    public CredentialManager(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences resolved;
        boolean isEncrypted;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            resolved = EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            isEncrypted = true;
            migrateFromLegacyIfNeeded(appContext, resolved);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "EncryptedSharedPreferences init failed; falling back to plain prefs", e);
            resolved = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
            isEncrypted = false;
        }
        this.prefs = resolved;
        this.encrypted = isEncrypted;
    }

    /**
     * One-shot migration: if the user upgraded from an older version that
     * stored credentials in plain SharedPreferences, copy them across and
     * wipe the legacy file so the plaintext copy is destroyed.
     */
    private void migrateFromLegacyIfNeeded(Context ctx, SharedPreferences secure) {
        if (secure.contains(KEY_CLIENT_CODE)) return; // already migrated
        SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        if (!legacy.contains(KEY_CLIENT_CODE)) return;

        Log.i(TAG, "Migrating credentials from legacy plain prefs to encrypted store");
        secure.edit()
                .putString(KEY_CLIENT_CODE, legacy.getString(KEY_CLIENT_CODE, ""))
                .putString(KEY_PIN,         legacy.getString(KEY_PIN, ""))
                .putString(KEY_JWT_TOKEN,   legacy.getString(KEY_JWT_TOKEN, ""))
                .putLong(KEY_TOKEN_TIMESTAMP, legacy.getLong(KEY_TOKEN_TIMESTAMP, 0))
                .apply();
        legacy.edit().clear().apply();
    }

    public boolean isUsingEncryption() { return encrypted; }

    public void save(String clientCode, String pin) {
        prefs.edit()
                .putString(KEY_CLIENT_CODE, clientCode)
                .putString(KEY_PIN, pin)
                .apply();
    }

    public String getClientCode() { return prefs.getString(KEY_CLIENT_CODE, ""); }
    public String getPin()        { return prefs.getString(KEY_PIN, ""); }

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

    public String getToken()         { return prefs.getString(KEY_JWT_TOKEN, ""); }
    public long getTokenTimestamp()  { return prefs.getLong(KEY_TOKEN_TIMESTAMP, 0); }

    public void clearToken() {
        prefs.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_TOKEN_TIMESTAMP)
                .apply();
    }

    // --- Upstox OAuth2 access token persistence ---

    public void saveUpstoxToken(String token, long timestamp) {
        prefs.edit()
                .putString(KEY_UPSTOX_TOKEN, token)
                .putLong(KEY_UPSTOX_TIMESTAMP, timestamp)
                .apply();
    }

    public String getUpstoxToken()        { return prefs.getString(KEY_UPSTOX_TOKEN, ""); }
    public long getUpstoxTokenTimestamp() { return prefs.getLong(KEY_UPSTOX_TIMESTAMP, 0); }

    public void clearUpstoxToken() {
        prefs.edit()
                .remove(KEY_UPSTOX_TOKEN)
                .remove(KEY_UPSTOX_TIMESTAMP)
                .apply();
    }

    /** Wipes everything (used by Logout and "Delete Account Data"). */
    public void clear() {
        prefs.edit().clear().apply();
    }
}

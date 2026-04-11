package com.example.personalfinancemanager;

/**
 * Owns the lifecycle of the Angel One JWT session token.
 *
 * <p>Replaces the static {@code jwtToken / tokenTimestamp / tokenLoaded} fields
 * that previously lived in {@code AngelOneHelper}. Static state caused two
 * concrete bugs:
 *   1) it could not be reset between unit tests, and
 *   2) creating a second {@code AngelOneHelper} instance silently shared the
 *      old token, masking auth failures during logout flows.
 *
 * <p>This class is thread-safe via {@code synchronized} on every accessor —
 * the API surface is small enough that lock contention is irrelevant compared
 * to the (10–100 ms) network calls these methods gate.
 */
public class AngelOneTokenManager {

    private static final long TOKEN_VALIDITY_MS = 23L * 60L * 60L * 1000L; // 23h

    private final CredentialManager credentialManager;

    private String token = "";
    private long timestamp = 0L;

    public AngelOneTokenManager(CredentialManager credentialManager) {
        this.credentialManager = credentialManager;
        // Load any persisted token from previous app sessions immediately, so
        // isValid() returns the right answer on the very first call.
        String saved = credentialManager.getToken();
        long savedTs = credentialManager.getTokenTimestamp();
        if (saved != null && !saved.isEmpty() && savedTs > 0) {
            this.token = saved;
            this.timestamp = savedTs;
        }
    }

    public synchronized boolean isValid() {
        return !token.isEmpty()
                && (System.currentTimeMillis() - timestamp) < TOKEN_VALIDITY_MS;
    }

    public synchronized String getToken() {
        return token;
    }

    public synchronized void update(String newToken) {
        this.token = newToken == null ? "" : newToken;
        this.timestamp = System.currentTimeMillis();
        credentialManager.saveToken(this.token, this.timestamp);
    }

    public synchronized void invalidate() {
        this.token = "";
        this.timestamp = 0L;
        credentialManager.clearToken();
    }
}

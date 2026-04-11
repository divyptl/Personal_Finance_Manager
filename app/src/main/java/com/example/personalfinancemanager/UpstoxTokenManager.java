package com.example.personalfinancemanager;

/**
 * Owns the lifecycle of the Upstox OAuth2 access token.
 *
 * <p>Upstox access tokens expire every trading day at 03:30 AM IST (roughly
 * 22:00 UTC the previous day). Rather than parsing the per-token expiry,
 * we treat any token older than 12 hours as expired — the user simply
 * re-authorizes via the OAuth flow when that happens.
 *
 * <p>Kept separate from {@link AngelOneTokenManager} so logout / token
 * invalidation for one broker does not affect the other.
 */
public class UpstoxTokenManager {

    private static final long TOKEN_VALIDITY_MS = 12L * 60L * 60L * 1000L; // 12h

    private final CredentialManager credentialManager;

    private String token = "";
    private long timestamp = 0L;

    public UpstoxTokenManager(CredentialManager credentialManager) {
        this.credentialManager = credentialManager;
        String saved = credentialManager.getUpstoxToken();
        long savedTs = credentialManager.getUpstoxTokenTimestamp();
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
        credentialManager.saveUpstoxToken(this.token, this.timestamp);
    }

    public synchronized void invalidate() {
        this.token = "";
        this.timestamp = 0L;
        credentialManager.clearUpstoxToken();
    }
}

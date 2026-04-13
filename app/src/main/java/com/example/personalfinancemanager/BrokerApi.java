package com.example.personalfinancemanager;

import java.util.List;
import java.util.Map;

/**
 * Broker-agnostic interface so the rest of the app does not depend on
 * Angel One specifically. Adding Zerodha / Upstox / Groww later means
 * implementing this interface — the activities, repositories, and view
 * models do not need to change.
 *
 * <p>All methods are asynchronous and post their callbacks on the main
 * thread, matching the existing call sites.
 */
public interface BrokerApi {

    interface AuthCallback {
        void onSuccess();
        void onFailure(String error);
    }

    interface HoldingsCallback {
        void onHoldingsFetched(List<Stock> holdings);
        /** Called instead of onHoldingsFetched if the request failed. */
        void onError(String error);
    }

    interface BatchLtpCallback {
        void onPricesFetched(Map<String, Double> priceMap);
    }

    /** True if a non-expired session token is currently held. */
    boolean isAuthenticated();

    /**
     * Exchanges a 6-digit TOTP code for a session token. Returns immediately
     * if a valid token is already cached.
     */
    void authenticate(String totpCode, AuthCallback callback);

    /** Fetches the user's current holdings. Requires authentication. */
    void fetchHoldings(HoldingsCallback callback);

    /** Fetches latest traded prices for a batch of stocks. */
    void fetchBatchLtp(List<Stock> stocks, BatchLtpCallback callback);

    /** Forgets the current session token (server-side logout is best-effort). */
    void logout();

    // ------------------------------------------------------------------
    // Optional OAuth2 hooks. Brokers that authenticate via TOTP (Angel One)
    // can ignore these; brokers that authenticate via OAuth2 redirect flow
    // (Upstox) override them.
    // ------------------------------------------------------------------

    /** True if this broker uses an OAuth2 web redirect to log in. */
    default boolean usesOAuthLogin() {
        return false;
    }

    /**
     * Returns the authorization URL the app should open in a browser /
     * Custom Tab. Only meaningful when {@link #usesOAuthLogin()} is true.
     * A null return means OAuth is not configured (e.g. missing client id).
     */
    @androidx.annotation.Nullable
    default String buildAuthorizationUrl() {
        return null;
    }

    /**
     * Exchanges an OAuth2 authorization code (captured by the redirect
     * handler) for an access token. Only meaningful when
     * {@link #usesOAuthLogin()} is true.
     */
    default void completeOAuthLogin(String authorizationCode, AuthCallback callback) {
        callback.onFailure("OAuth not supported by this broker");
    }

    /** Stable machine-readable identifier, e.g. "AngelOne" or "Upstox". */
    default String brokerId() {
        return getClass().getSimpleName();
    }
}

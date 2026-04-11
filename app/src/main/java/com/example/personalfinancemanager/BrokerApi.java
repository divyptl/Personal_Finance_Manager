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
}

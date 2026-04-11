package com.example.personalfinancemanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Receives the OAuth2 redirect from Upstox (scheme {@code wealthflow://upstox/callback})
 * and hands the authorization code off to {@link UpstoxBrokerApi#completeOAuthLogin}
 * to be exchanged for an access token. Then returns the user to
 * {@link PortfolioActivity}.
 *
 * <p>This activity has no UI of its own — it's a trampoline. The Custom Tab
 * that performed the login closes automatically when the redirect fires.
 */
public class UpstoxOAuthCallbackActivity extends AppCompatActivity {

    private static final String TAG = "UpstoxOAuthCallback";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleRedirect(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRedirect(intent);
    }

    private void handleRedirect(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        String code = data != null ? data.getQueryParameter("code") : null;
        String error = data != null ? data.getQueryParameter("error") : null;

        if (error != null && !error.isEmpty()) {
            Log.w(TAG, "Upstox OAuth error: " + error);
            Toast.makeText(this,
                    getString(R.string.error_upstox_oauth, error),
                    Toast.LENGTH_LONG).show();
            returnToPortfolio();
            return;
        }
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, R.string.error_upstox_no_code, Toast.LENGTH_LONG).show();
            returnToPortfolio();
            return;
        }

        BrokerApi upstox = ServiceLocator.get(this).upstoxBrokerApi();
        upstox.completeOAuthLogin(code, new BrokerApi.AuthCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(UpstoxOAuthCallbackActivity.this,
                        R.string.toast_upstox_connected, Toast.LENGTH_SHORT).show();
                returnToPortfolio();
            }

            @Override
            public void onFailure(String err) {
                Toast.makeText(UpstoxOAuthCallbackActivity.this,
                        getString(R.string.error_upstox_auth, err),
                        Toast.LENGTH_LONG).show();
                returnToPortfolio();
            }
        });
    }

    private void returnToPortfolio() {
        Intent intent = new Intent(this, PortfolioActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

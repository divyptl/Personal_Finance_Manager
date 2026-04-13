package com.example.personalfinancemanager;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Hosts a WebView for Upstox OAuth2 login.
 *
 * <p>Why WebView instead of an external browser?
 * Upstox's developer portal only accepts https:// redirect URIs — it rejects
 * custom schemes like {@code wealthflow://}. By hosting the login page in a
 * WebView we can intercept the redirect the moment Upstox navigates to it,
 * extract the {@code ?code=} parameter ourselves, and exchange it for a token
 * — the redirect URL never actually needs to be a real server.
 *
 * <p>Setup in the Upstox developer portal:
 * Set the redirect URI to {@code https://wealthflow.app/callback}
 * (or any https URL you own / control — it is never actually opened).
 * Then set the same value in {@code local.properties}:
 * {@code UPSTOX_REDIRECT_URI=https://wealthflow.app/callback}
 *
 * <p>Result codes:
 * <ul>
 *   <li>{@link #RESULT_OK} — authentication succeeded</li>
 *   <li>{@link #RESULT_CANCELED} — user dismissed or an error occurred;
 *       check extra {@link #EXTRA_ERROR} for the error message</li>
 * </ul>
 */
public class UpstoxLoginActivity extends AppCompatActivity {

    public static final String EXTRA_ERROR = "upstox_error";

    private WebView webView;
    private ProgressBar progressBar;
    private BrokerApi upstoxBrokerApi;
    private String redirectUri;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upstox_login);

        upstoxBrokerApi = ServiceLocator.get(this).upstoxBrokerApi();
        redirectUri = BuildConfig.UPSTOX_REDIRECT_URI;

        ImageButton btnClose = findViewById(R.id.btnClose);
        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        btnClose.setOnClickListener(v -> cancel("User cancelled"));

        // Build the authorization URL
        String authUrl = upstoxBrokerApi.buildAuthorizationUrl();
        if (authUrl == null) {
            cancel(getString(R.string.error_upstox_not_configured));
            return;
        }

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                    WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Intercept when Upstox navigates to our redirect URI
                if (url.startsWith(redirectUri)) {
                    handleRedirect(url);
                    return true; // block the WebView from actually loading it
                }
                return false; // let WebView handle all other URLs normally
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.loadUrl(authUrl);
    }

    /**
     * Called when the WebView is about to navigate to our redirect URI.
     * Extracts the {@code code} parameter and exchanges it for an access token.
     */
    private void handleRedirect(String url) {
        android.net.Uri uri = android.net.Uri.parse(url);
        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");

        if (error != null) {
            cancel("Upstox login error: " + error);
            return;
        }
        if (code == null || code.isEmpty()) {
            cancel("No authorization code received");
            return;
        }

        // Show progress while exchanging the code for a token
        progressBar.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);

        upstoxBrokerApi.completeOAuthLogin(code, new BrokerApi.AuthCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(UpstoxLoginActivity.this,
                        R.string.toast_upstox_connected, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(String error) {
                cancel(error);
            }
        });
    }

    private void cancel(String reason) {
        Intent result = new Intent();
        result.putExtra(EXTRA_ERROR, reason);
        setResult(RESULT_CANCELED, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            cancel("User cancelled");
        }
    }
}

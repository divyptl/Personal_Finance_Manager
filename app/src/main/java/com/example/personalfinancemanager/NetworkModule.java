package com.example.personalfinancemanager;

import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Constructs the single shared {@link OkHttpClient} used by all network
 * collaborators in the app.
 *
 * <p>Adds three interceptors:
 *   1. <b>Standard headers</b> — Content-Type, X-UserType, X-SourceID, etc.
 *      The previous code attached these manually on every request and
 *      repeatedly used the placeholder {@code 127.0.0.1}, which violates
 *      Angel One's API ToS. We now compute the real local IPv4 once at
 *      construction time.
 *   2. <b>Logging</b> — body-level in debug, headers-only in release. Driven
 *      by {@link BuildConfig#DEBUG} so we never accidentally log JWTs in prod.
 *   3. <b>Certificate pinning</b> — only attached if a non-empty pin was
 *      provided via {@code BuildConfig.ANGELONE_CERT_PIN}. Without a pin we
 *      fall back to system trust anchors (still TLS, just not pinned).
 */
public class NetworkModule {

    private static final String TAG = "NetworkModule";
    private static final String ANGEL_HOST = "apiconnect.angelbroking.com";

    private final OkHttpClient client;
    private final String localIpv4;

    public NetworkModule() {
        this.localIpv4 = resolveLocalIpv4();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        if (BuildConfig.DEBUG) {
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            logging.redactHeader("Authorization");
            logging.redactHeader("X-PrivateKey");
        } else {
            logging.setLevel(HttpLoggingInterceptor.Level.NONE);
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new StandardHeadersInterceptor(localIpv4))
                .addInterceptor(logging);

        // Attach certificate pinning iff a pin was provided at build time.
        // The pin string must be in OkHttp's "sha256/<base64>" format. See the
        // openssl recipe in app/build.gradle.kts for how to compute it.
        String pin = BuildConfig.ANGELONE_CERT_PIN;
        if (pin != null && !pin.isEmpty() && pin.startsWith("sha256/")) {
            CertificatePinner pinner = new CertificatePinner.Builder()
                    .add(ANGEL_HOST, pin)
                    .build();
            builder.certificatePinner(pinner);
            Log.i(TAG, "Certificate pinning ENABLED for " + ANGEL_HOST);
        } else {
            Log.w(TAG, "ANGELONE_CERT_PIN not set — TLS will use system trust only. "
                    + "Set it in local.properties before publishing a release build.");
        }

        this.client = builder.build();
    }

    public OkHttpClient okHttpClient() { return client; }
    public String localIpv4() { return localIpv4; }

    /**
     * Best-effort: walks active network interfaces and returns the first
     * non-loopback IPv4 address. Falls back to 0.0.0.0 if nothing usable
     * is found (e.g. no network connection at app start).
     */
    private static String resolveLocalIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve local IPv4", e);
        }
        return "0.0.0.0";
    }
}

package com.example.personalfinancemanager;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the boilerplate headers Angel One's API expects to every
 * outgoing request. Centralizing this in an interceptor (rather than
 * each call site like the old {@code addStandardHeaders} helper) means
 * adding a new endpoint can never accidentally forget the API key or
 * source identifier.
 *
 * <p>The X-PrivateKey header pulls its value from {@link BuildConfig},
 * which in turn pulls it from {@code local.properties} at build time.
 * This is the kill-switch for the previously hardcoded
 * {@code "YGWfQ7oP"} string in source.
 */
public class StandardHeadersInterceptor implements Interceptor {

    private final String localIp;

    public StandardHeadersInterceptor(String localIp) {
        this.localIp = localIp == null ? "0.0.0.0" : localIp;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        // Don't override headers the caller explicitly set (e.g. multipart
        // Content-Type), only add those that are missing.
        Request.Builder b = original.newBuilder();
        if (original.header("Content-Type") == null) {
            b.header("Content-Type", "application/json");
        }
        if (original.header("Accept") == null) {
            b.header("Accept", "application/json");
        }
        b.header("X-UserType", "USER");
        b.header("X-SourceID", "WEB");
        b.header("X-ClientLocalIP", localIp);
        b.header("X-ClientPublicIP", localIp);
        b.header("X-MACAddress", "00:00:00:00:00:00");
        b.header("X-PrivateKey", BuildConfig.ANGELONE_API_KEY);

        return chain.proceed(b.build());
    }
}

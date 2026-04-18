package com.example.personalfinancemanager;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

/**
 * Single-shot re-authentication gate for destructive / high-trust actions
 * (delete-all transactions, logout-broker, delete saved credentials).
 *
 * <p>Usage: {@code BiometricGate.require(activity, title, subtitle, () -> doTheThing());}
 *
 * <p>Falls open (runs the action without prompting) on devices that can't
 * authenticate at all — we don't want to lock the user out of destructive
 * actions on a device with no PIN set. Callers who need strict enforcement
 * should check {@link CredentialManager#isBiometricLockEnabled()} themselves
 * before calling.
 */
public final class BiometricGate {

    private BiometricGate() {}

    public interface OnAuthenticated {
        void run();
    }

    /**
     * Timestamp of the most recent successful authentication (gate success
     * OR explicit {@link #markAuthenticated()} from the launcher lock). In-
     * process only — process death resets the grace window, which is fine
     * because app-lock will re-prompt on cold start anyway.
     */
    private static volatile long lastAuthAt = 0L;

    /** Five-minute grace window for re-entering recently-authenticated screens. */
    public static final long DEFAULT_GRACE_MS = 5L * 60L * 1000L;

    /** Called by LockActivity on a successful app-unlock. */
    public static void markAuthenticated() {
        lastAuthAt = System.currentTimeMillis();
    }

    /** True if the user authenticated within the last {@code withinMillis}. */
    public static boolean hasRecentAuth(long withinMillis) {
        return System.currentTimeMillis() - lastAuthAt < withinMillis;
    }

    public static void require(@NonNull FragmentActivity activity,
                                @NonNull String title,
                                @NonNull String subtitle,
                                @NonNull OnAuthenticated onAuth) {
        require(activity, title, subtitle, onAuth, null);
    }

    /**
     * Variant with an explicit cancel hook. {@code onCancel} fires when the
     * user dismisses the prompt (Cancel / Back) or the prompt errors out
     * non-recoverably. Callers that block content behind the prompt use this
     * to unwind their UI instead of relying on a silent timeout.
     */
    public static void require(@NonNull FragmentActivity activity,
                                @NonNull String title,
                                @NonNull String subtitle,
                                @NonNull OnAuthenticated onAuth,
                                @androidx.annotation.Nullable Runnable onCancel) {
        int auths = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int canAuth = BiometricManager.from(activity).canAuthenticate(auths);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // No way to authenticate — fall open rather than silently block.
            onAuth.run();
            return;
        }

        BiometricPrompt prompt = new BiometricPrompt(activity,
                androidx.core.content.ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        markAuthenticated();
                        onAuth.run();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        // Silent on user-cancel; toast otherwise.
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                && errorCode != BiometricPrompt.ERROR_CANCELED) {
                            Toast.makeText(activity, errString, Toast.LENGTH_SHORT).show();
                        }
                        if (onCancel != null) onCancel.run();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(auths)
                .build();
        prompt.authenticate(info);
    }
}

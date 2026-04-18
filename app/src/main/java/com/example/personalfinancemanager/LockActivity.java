package com.example.personalfinancemanager;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * Gatekeeper activity shown on app launch when the user has opted into
 * biometric app lock. Launches {@link MainActivity} on successful auth and
 * finishes itself so it's not on the back stack.
 *
 * <p>Designed to be the LAUNCHER activity — if the lock is disabled, or the
 * device has no biometric hardware / no enrolled fingerprint, we fall through
 * to MainActivity immediately so the user is never trapped.
 *
 * <p>The device-credential fallback (PIN/pattern/password) is enabled via
 * {@link BiometricPrompt.PromptInfo.Builder#setAllowedAuthenticators} — users
 * who disable fingerprint can still unlock with their device lock-screen PIN.
 */
public class LockActivity extends AppCompatActivity {

    private TextView btnRetry;
    private TextView textStatus;

    // Allow biometric (strong or weak) OR the device credential as fallback.
    // BIOMETRIC_WEAK covers face unlock on most devices; DEVICE_CREDENTIAL
    // lets the user fall back to their lockscreen PIN/pattern/password if
    // fingerprint fails or isn't enrolled.
    private static final int ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    /**
     * Grace period: if the user authenticated less than this many ms ago, we
     * skip the prompt on the next foreground. Prevents the lock from feeling
     * punitive when quickly switching to a URL and back, or when the OS kicks
     * us out and immediately restores (config changes, transient low-memory).
     * 60 seconds matches what banking apps like CRED/GPay use.
     */
    public static final long AUTH_GRACE_MS = 60_000L;

    /** Wall-clock ms of the most recent successful auth. 0 = never. */
    public static volatile long sLastAuthenticatedAt = 0L;

    /** True iff we authenticated recently enough to skip a fresh prompt. */
    public static boolean isWithinGracePeriod() {
        return sLastAuthenticatedAt > 0
                && System.currentTimeMillis() - sLastAuthenticatedAt < AUTH_GRACE_MS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CredentialManager cm = new CredentialManager(this);

        // First-run: show the onboarding carousel before anything else.
        // OnboardingActivity marks the flag complete and relaunches LockActivity,
        // at which point this check short-circuits.
        if (!cm.isOnboardingCompleted()) {
            Intent onboarding = new Intent(this, OnboardingActivity.class);
            onboarding.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(onboarding);
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        // If the user hasn't opted in, or the device can't actually do biometrics,
        // skip this activity entirely — we must never block a user out of their
        // own data over a flaky sensor.
        if (!cm.isBiometricLockEnabled() || !canAuthenticate()) {
            launchMain();
            return;
        }

        // Recent auth? Skip the prompt — but refresh the stamp so the clock
        // resets from this moment (user is clearly active).
        if (isWithinGracePeriod()) {
            sLastAuthenticatedAt = System.currentTimeMillis();
            launchMain();
            return;
        }

        setContentView(R.layout.activity_lock);
        btnRetry = findViewById(R.id.btnRetry);
        textStatus = findViewById(R.id.textLockStatus);
        btnRetry.setOnClickListener(v -> promptForAuth());

        promptForAuth();
    }

    private boolean canAuthenticate() {
        BiometricManager bm = BiometricManager.from(this);
        int result = bm.canAuthenticate(ALLOWED_AUTHENTICATORS);
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void promptForAuth() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        sLastAuthenticatedAt = System.currentTimeMillis();
                        // Propagate to the shared gate so screens that re-prompt
                        // on sensitive actions respect the grace window.
                        BiometricGate.markAuthenticated();
                        launchMain();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        // User cancelled, too many attempts, or hardware not available.
                        // We keep the retry button visible so they can try again —
                        // but if the user pressed Back/Cancel we should NOT auto-retry,
                        // or we'd trap them in a loop.
                        if (textStatus != null) {
                            textStatus.setText(errString);
                        }
                        if (btnRetry != null) {
                            btnRetry.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Wrong finger / face not recognized — system shows its own
                        // feedback, we just make sure retry is obvious.
                        if (btnRetry != null) {
                            btnRetry.setVisibility(View.VISIBLE);
                        }
                    }
                });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setSubtitle(getString(R.string.lock_subtitle))
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS);

        // setNegativeButtonText() is incompatible with DEVICE_CREDENTIAL —
        // the framework provides the "Use PIN" button automatically in that case.
        prompt.authenticate(builder.build());
    }

    private void launchMain() {
        Intent intent = new Intent(this, MainActivity.class);
        // NEW_TASK keeps us from stacking on top of a prior session if the OS
        // restored this activity from saved state.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Skip the slide animation — the lock should feel instantaneous.
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }
}

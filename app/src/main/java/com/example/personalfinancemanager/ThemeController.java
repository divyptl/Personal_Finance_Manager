package com.example.personalfinancemanager;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Thin wrapper around {@link AppCompatDelegate#setDefaultNightMode} that
 * maps the persisted {@code theme_mode} preference string to the correct
 * framework constant. Centralizing this keeps the "what mode is active"
 * logic in one place so new call sites (settings dialog, Application
 * bootstrap) can't drift apart.
 */
public final class ThemeController {

    private ThemeController() {}

    /** Applies the persisted theme to the process. Idempotent. */
    public static void applyFromPrefs(CredentialManager credentialManager) {
        if (credentialManager == null) return;
        applyMode(credentialManager.getThemeMode());
    }

    /** Applies a mode token directly. Does NOT persist — caller does that. */
    public static void applyMode(String mode) {
        int night;
        if (CredentialManager.THEME_LIGHT.equals(mode)) {
            night = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (CredentialManager.THEME_DARK.equals(mode)) {
            night = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(night);
    }
}

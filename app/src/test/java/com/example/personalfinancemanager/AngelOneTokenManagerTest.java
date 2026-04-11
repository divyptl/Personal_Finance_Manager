package com.example.personalfinancemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

/**
 * Unit tests for {@link AngelOneTokenManager}.
 *
 * <p>These guard against the regression that motivated extracting the class
 * in the first place: the previous static fields meant a stale token from a
 * previous test (or previous logged-out user) could leak into the next call.
 */
public class AngelOneTokenManagerTest {

    @Test
    public void invalid_when_no_token_present() {
        CredentialManager cm = mock(CredentialManager.class);
        when(cm.getToken()).thenReturn("");
        when(cm.getTokenTimestamp()).thenReturn(0L);

        AngelOneTokenManager mgr = new AngelOneTokenManager(cm);
        assertFalse(mgr.isValid());
    }

    @Test
    public void valid_when_persisted_token_is_fresh() {
        CredentialManager cm = mock(CredentialManager.class);
        when(cm.getToken()).thenReturn("abc.def.ghi");
        when(cm.getTokenTimestamp()).thenReturn(System.currentTimeMillis() - 60_000L);

        AngelOneTokenManager mgr = new AngelOneTokenManager(cm);
        assertTrue(mgr.isValid());
        assertEquals("abc.def.ghi", mgr.getToken());
    }

    @Test
    public void invalid_when_persisted_token_is_expired() {
        CredentialManager cm = mock(CredentialManager.class);
        when(cm.getToken()).thenReturn("old.token");
        // 24h ago — beyond the 23h validity window
        when(cm.getTokenTimestamp()).thenReturn(
                System.currentTimeMillis() - (24L * 60L * 60L * 1000L));

        AngelOneTokenManager mgr = new AngelOneTokenManager(cm);
        assertFalse(mgr.isValid());
    }

    @Test
    public void update_persists_to_credential_manager() {
        CredentialManager cm = mock(CredentialManager.class);
        when(cm.getToken()).thenReturn("");
        when(cm.getTokenTimestamp()).thenReturn(0L);

        AngelOneTokenManager mgr = new AngelOneTokenManager(cm);
        mgr.update("new.jwt.token");

        assertTrue(mgr.isValid());
        assertEquals("new.jwt.token", mgr.getToken());
        verify(cm).saveToken(anyString(), anyLong());
    }

    @Test
    public void invalidate_clears_token_and_persistence() {
        CredentialManager cm = mock(CredentialManager.class);
        when(cm.getToken()).thenReturn("existing");
        when(cm.getTokenTimestamp()).thenReturn(System.currentTimeMillis());

        AngelOneTokenManager mgr = new AngelOneTokenManager(cm);
        assertTrue(mgr.isValid());

        mgr.invalidate();

        assertFalse(mgr.isValid());
        assertEquals("", mgr.getToken());
        verify(cm).clearToken();
    }
}

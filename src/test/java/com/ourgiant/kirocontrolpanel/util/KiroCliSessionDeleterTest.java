package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KiroCliSessionDeleterTest {

    /** Regression test for issue #124's original throwaway-session cleanup, now shared with
     * #126's bulk clean-up: a failure here shouldn't throw, since every caller treats deletion
     * as best-effort cleanup where "try again later" is an acceptable outcome. */
    @Test
    void deleteNeverThrowsAndReturnsFalseWhenTheBinaryIsMissing() {
        boolean result = assertDoesNotThrow(() ->
            KiroCliSessionDeleter.delete("kiro-cli-binary-that-does-not-exist-anywhere",
                "6f606f51-fff7-4c14-9b13-22d1571f5d8c"));

        assertFalse(result);
    }

    /** sessionId can come from a session file's own JSON content (SessionManifest.sessionId(),
     * #126's bulk clean-up), not just kiro-cli's own trusted ACP response -- reject anything that
     * isn't a well-formed UUID before it ever reaches ProcessBuilder, rather than passing an
     * arbitrary string through as a subprocess argument. See #136. */
    @Test
    void deleteRejectsAMalformedSessionIdWithoutSpawningAProcess() {
        boolean result = assertDoesNotThrow(() ->
            KiroCliSessionDeleter.delete("kiro-cli-binary-that-does-not-exist-anywhere", "--not-a-uuid"));

        assertFalse(result);
    }

    @Test
    void deleteRejectsANullSessionId() {
        boolean result = assertDoesNotThrow(() ->
            KiroCliSessionDeleter.delete("kiro-cli-binary-that-does-not-exist-anywhere", null));

        assertFalse(result);
    }
}

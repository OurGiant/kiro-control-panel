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
            KiroCliSessionDeleter.delete("kiro-cli-binary-that-does-not-exist-anywhere", "some-session-id"));

        assertFalse(result);
    }
}

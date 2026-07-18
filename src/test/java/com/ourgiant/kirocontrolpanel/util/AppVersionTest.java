package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppVersionTest {

    @Test
    void resolveNeverReturnsNull() {
        assertNotNull(AppVersion.resolve());
    }

    @Test
    void resolveNeverReturnsBlank() {
        assertFalse(AppVersion.resolve().isBlank());
    }
}

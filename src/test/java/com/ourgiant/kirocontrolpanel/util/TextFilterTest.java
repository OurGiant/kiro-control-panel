package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFilterTest {

    @Test
    void blankOrNullQueryMatchesEverything() {
        assertTrue(TextFilter.matches("anything", ""));
        assertTrue(TextFilter.matches("anything", "   "));
        assertTrue(TextFilter.matches("anything", null));
        assertTrue(TextFilter.matches(null, null));
        assertTrue(TextFilter.matches(null, ""));
    }

    @Test
    void matchIsCaseInsensitiveSubstring() {
        assertTrue(TextFilter.matches("Steering Doc.md", "doc"));
        assertTrue(TextFilter.matches("Steering Doc.md", "STEERING"));
        assertTrue(TextFilter.matches("Steering Doc.md", "Doc.md"));
        assertFalse(TextFilter.matches("Steering Doc.md", "xyz"));
    }

    @Test
    void nullHaystackWithNonBlankQueryNeverMatches() {
        assertFalse(TextFilter.matches(null, "x"));
    }
}

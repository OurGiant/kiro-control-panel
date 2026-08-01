package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwingLayoutUtilsTest {

    @Test
    void suggestsBaseNamePlusCopyWhenNothingIsTaken() {
        assertEquals("alpha-copy", SwingLayoutUtils.suggestCopyName("alpha", taken -> false));
    }

    @Test
    void fallsBackToNumberedSuffixesUntilOneIsFree() {
        Set<String> taken = Set.of("alpha-copy", "alpha-copy-2", "alpha-copy-3");

        assertEquals("alpha-copy-4", SwingLayoutUtils.suggestCopyName("alpha", taken::contains));
    }
}

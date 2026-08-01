package com.ourgiant.kirocontrolpanel.util;

import java.util.Locale;

/**
 * Case-insensitive substring match used by every panel's live filter field
 * (MCP/Steering/Skills/Hooks/Agents). A blank or {@code null} query matches
 * everything, so an empty filter field shows the full unfiltered list.
 */
public final class TextFilter {

    private TextFilter() {
    }

    public static boolean matches(String haystack, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}

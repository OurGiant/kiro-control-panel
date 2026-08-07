package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionsTableModelTest {

    private SessionManifest someManifest(String title) {
        return new SessionManifest(
            "abc-123", "/home/example/demo",
            Instant.parse("2026-08-07T11:26:45Z").toEpochMilli(),
            Instant.parse("2026-08-07T11:29:38Z").toEpochMilli(),
            title, "interactive", 4,
            List.of("/home/example/demo/out.txt"), List.of("/home/example/demo"),
            "/home/x/.kiro/sessions/cli/abc-123.json", "/home/x/.kiro/sessions/cli/abc-123.jsonl");
    }

    @Test
    void emptyModelHasNoRows() {
        SessionsTableModel model = new SessionsTableModel();

        assertEquals(0, model.getRowCount());
        assertEquals(4, model.getColumnCount());
    }

    @Test
    void setSessionsPopulatesRowsAndColumns() {
        SessionsTableModel model = new SessionsTableModel();

        model.setSessions(List.of(someManifest("what should I change and why")));

        assertEquals(1, model.getRowCount());
        assertEquals("2026-08-07 11:26", model.getValueAt(0, 0));
        assertEquals("/home/example/demo", model.getValueAt(0, 1));
        assertEquals("what should I change and why", model.getValueAt(0, 2));
        assertEquals(1, model.getValueAt(0, 3));
        assertEquals(someManifest("what should I change and why"), model.sessionAt(0));
    }

    @Test
    void snippetShowsPlaceholderForNullOrBlankTitle() {
        assertEquals("(no prompt)", SessionsTableModel.snippet(null));
        assertEquals("(no prompt)", SessionsTableModel.snippet("   "));
    }

    @Test
    void snippetTruncatesLongTitlesAndCollapsesNewlines() {
        String longTitle = "a".repeat(120);

        String result = SessionsTableModel.snippet(longTitle);

        assertEquals(83, result.length()); // 80 chars + "..."
    }
}

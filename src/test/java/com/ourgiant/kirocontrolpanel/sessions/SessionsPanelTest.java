package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsPanelTest {

    @TempDir
    Path tempDir;

    private void writeSession(Path dir, String sessionId, String cwd, String title, String promptText) throws IOException {
        Files.writeString(dir.resolve(sessionId + ".json"), """
            {
              "session_id": "%s",
              "cwd": "%s",
              "created_at": "2026-08-07T11:26:45.323381992Z",
              "updated_at": "2026-08-07T11:29:38.111897334Z",
              "title": "%s",
              "session_created_reason": "interactive"
            }
            """.formatted(sessionId, cwd, title));
        Files.writeString(dir.resolve(sessionId + ".jsonl"), """
            {"version":"v1","kind":"Prompt","data":{"message_id":"m1","content":[{"kind":"text","data":"%s"}],"meta":{"timestamp":1}}}
            """.formatted(promptText));
    }

    private SessionIndexService indexServiceWithTwoSessions() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions-cli");
        Files.createDirectories(sessionsDir);
        writeSession(sessionsDir, "session-a", "/home/example/project-a", "refactor the auth module", "refactor the auth module");
        writeSession(sessionsDir, "session-b", "/home/example/project-b", "write unit tests", "write unit tests");

        SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"));
        service.scanAndIndex(sessionsDir);
        return service;
    }

    /**
     * Regression test for a usability finding from UAT on issue #117: the filter field's
     * dual live-filter/Enter-to-search behavior had no visual cue, so a user typing a keyword
     * read the live-filtered (cwd/title only) results as "search doesn't reach the full
     * conversation" when pressing Enter would have found it. Confirms the explanatory
     * placeholder text and tooltip are actually wired up, not just present in a comment.
     */
    @Test
    void filterFieldExplainsItsDualLiveFilterAndSearchBehavior() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            Object placeholder = panel.getFilterField().getClientProperty("JTextField.placeholderText");
            assertTrue(placeholder instanceof String text && text.contains("Enter") && text.contains("search"));
            assertTrue(panel.getFilterField().getToolTipText().contains("full-text search"));
        }
    }

    @Test
    void loadsAllIndexedSessionsOnConstruction() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            assertEquals(2, panel.getTable().getRowCount());
            assertEquals("2 sessions indexed", panel.getStatusLabel().getText());
        }
    }

    @Test
    void liveFilterNarrowsRowsByCwdAsYouType() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            panel.getFilterField().setText("project-a");

            assertEquals(1, panel.getTable().getRowCount());
        }
    }

    @Test
    void selectingARowEnablesDetailActionsAndClearingSelectionDisablesThem() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);
            assertFalse(panel.getViewTranscriptButton().isEnabled());
            assertFalse(panel.getViewRawJsonButton().isEnabled());

            panel.getTable().setRowSelectionInterval(0, 0);
            assertTrue(panel.getViewTranscriptButton().isEnabled());
            assertTrue(panel.getViewRawJsonButton().isEnabled());

            panel.getTable().clearSelection();
            assertFalse(panel.getViewTranscriptButton().isEnabled());
            assertFalse(panel.getViewRawJsonButton().isEnabled());
        }
    }

    @Test
    void revealFileButtonIsDisabledUntilAFileIsSelectedInTheDetailList() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            panel.getTable().setRowSelectionInterval(0, 0);

            // Neither fixture session has a files-touched entry (only FileRead was used in
            // the real seeded data this schema was confirmed against) -- the list is empty,
            // so Reveal File has nothing to enable for.
            assertEquals(0, panel.getFilesList().getModel().getSize());
            assertFalse(panel.getRevealFileButton().isEnabled());
        }
    }

    @Test
    void enterInFilterFieldRunsFullTextSearchAndNarrowsToMatches() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            panel.getFilterField().setText("refactor*");
            for (var listener : panel.getFilterField().getActionListeners()) {
                listener.actionPerformed(null);
            }

            assertEquals(1, panel.getTable().getRowCount());
        }
    }

    /**
     * Regression test for a bug caught only by end-to-end verification against real data
     * (issue #117): a background reload triggered while search results are showing (e.g. the
     * live watcher indexing a newly-arrived session) used to fall back to the plain substring
     * live-filter using whatever text was left in the field -- since an FTS query like
     * "refactor*" rarely also matches as a literal cwd/title substring, this silently emptied
     * or changed the visible results out from under the user. A reload while a search is active
     * must re-run that same search instead.
     */
    @Test
    void backgroundReloadWhileSearchResultsAreShowingReRunsTheSearchInsteadOfFallingBackToLiveFilter() throws Exception {
        try (SessionIndexService indexService = indexServiceWithTwoSessions()) {
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), indexService);

            panel.getFilterField().setText("refactor*");
            for (var listener : panel.getFilterField().getActionListeners()) {
                listener.actionPerformed(null);
            }
            assertEquals(1, panel.getTable().getRowCount());

            panel.reload(); // simulates the update listener firing after a live background index

            assertEquals(1, panel.getTable().getRowCount());
        }
    }
}

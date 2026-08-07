package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManifestTest {

    private static SessionManifest manifest(String title, int messageCount) {
        return new SessionManifest("id", "/tmp", 0, 0, title, "interactive", messageCount,
            List.of(), List.of(), "/tmp/id.json", "/tmp/id.jsonl");
    }

    @Test
    void isEmptyWhenNoTitleAndNoMessages() {
        assertTrue(manifest(null, 0).isEmpty());
    }

    @Test
    void isNotEmptyWhenTitleIsPresentEvenWithNoMessages() {
        // A title without messages shouldn't happen from the real parser (title falls back to
        // the first prompt's text), but a manually-set sidecar title should still count as "not
        // empty" -- isEmpty() is about whether there's anything worth showing, not just message count.
        assertFalse(manifest("something", 0).isEmpty());
    }

    @Test
    void isNotEmptyWhenMessagesArePresentEvenWithNoTitle() {
        assertFalse(manifest(null, 3).isEmpty());
    }
}

package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTranscriptViewerDialogTest {

    @Test
    void formatTranscriptLabelsEachMessageByRoleInOrder() {
        List<TranscriptMessage> messages = List.of(
            new TranscriptMessage("m1", "user", "read the code and tell me what you think"),
            new TranscriptMessage("m2", "assistant", "looks good, a few suggestions below"));

        String formatted = SessionTranscriptViewerDialog.formatTranscript(messages);

        assertEquals("""
            You:
            read the code and tell me what you think

            Kiro:
            looks good, a few suggestions below""", formatted);
    }

    @Test
    void formatTranscriptOfNoMessagesExplainsTheresNothingToShowRatherThanBeingBlank() {
        String formatted = SessionTranscriptViewerDialog.formatTranscript(List.of());

        assertEquals("(No prompts or replies found in this transcript.)", formatted);
    }

    @Test
    void formatTranscriptLabelsAnUnrecognizedRoleWithItselfRatherThanGuessing() {
        List<TranscriptMessage> messages = List.of(new TranscriptMessage("m1", "system", "some future role"));

        String formatted = SessionTranscriptViewerDialog.formatTranscript(messages);

        assertEquals("system:\nsome future role", formatted);
    }

    @Test
    void findMatchesIsCaseInsensitiveAndReturnsOffsetsInEncounterOrder() {
        List<Integer> matches = SessionTranscriptViewerDialog.findMatches(
            "Refactor the auth module, then refactor the tests too", "refactor");

        assertEquals(List.of(0, 31), matches);
    }

    @Test
    void findMatchesDoesNotOverlapAConsumedMatch() {
        // "aaa" against needle "aa" -- a naive overlapping search would find offsets 0 and 1;
        // this should consume each match's full length before looking for the next one.
        List<Integer> matches = SessionTranscriptViewerDialog.findMatches("aaa", "aa");

        assertEquals(List.of(0), matches);
    }

    @Test
    void findMatchesOfAnEmptyOrBlankNeedleFindsNothingRatherThanEveryPosition() {
        assertTrue(SessionTranscriptViewerDialog.findMatches("some text", "").isEmpty());
        assertTrue(SessionTranscriptViewerDialog.findMatches("some text", null).isEmpty());
    }

    @Test
    void findMatchesOfATermNotPresentIsEmpty() {
        assertTrue(SessionTranscriptViewerDialog.findMatches("some text", "xyz").isEmpty());
    }
}

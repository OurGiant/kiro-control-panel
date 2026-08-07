package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

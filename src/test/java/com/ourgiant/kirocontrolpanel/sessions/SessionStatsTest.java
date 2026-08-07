package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionStatsTest {

    @Test
    void summarizeCountsMessagesByRoleAndTracksTheLongestReply() {
        List<TranscriptMessage> messages = List.of(
            new TranscriptMessage("m1", "user", "read the code and tell me what you think"),
            new TranscriptMessage("m2", "assistant", "looks good overall"),
            new TranscriptMessage("m3", "user", "anything else"),
            new TranscriptMessage("m4", "assistant", "one more thing worth calling out here"));

        SessionStats.Summary summary = SessionStats.summarize(messages, Duration.ofMinutes(5));

        assertEquals(2, summary.userMessageCount());
        assertEquals(2, summary.assistantMessageCount());
        assertEquals(8 + 3 + 3 + 7, summary.totalWordCount()); // "one more thing worth calling out here" = 7 words
        assertEquals(7, summary.longestReplyWordCount());
        assertEquals(Duration.ofMinutes(5), summary.duration());
    }

    @Test
    void summarizeOfNoMessagesIsAllZeroesNotAnError() {
        SessionStats.Summary summary = SessionStats.summarize(List.of(), Duration.ZERO);

        assertEquals(0, summary.userMessageCount());
        assertEquals(0, summary.assistantMessageCount());
        assertEquals(0, summary.totalWordCount());
        assertEquals(0, summary.longestReplyWordCount());
    }

    @Test
    void formatDurationRendersHoursMinutesAndSecondsAtTheAppropriateGranularity() {
        assertEquals("Messages: 0 (0 you / 0 Kiro)\nDuration: 45s\nWords exchanged: 0\nLongest reply: 0 words",
            SessionStats.format(new SessionStats.Summary(0, 0, 0, 0, Duration.ofSeconds(45))));
        assertEquals("Messages: 0 (0 you / 0 Kiro)\nDuration: 3m 5s\nWords exchanged: 0\nLongest reply: 0 words",
            SessionStats.format(new SessionStats.Summary(0, 0, 0, 0, Duration.ofSeconds(185))));
        assertEquals("Messages: 0 (0 you / 0 Kiro)\nDuration: 1h 5m\nWords exchanged: 0\nLongest reply: 0 words",
            SessionStats.format(new SessionStats.Summary(0, 0, 0, 0, Duration.ofMinutes(65))));
    }
}

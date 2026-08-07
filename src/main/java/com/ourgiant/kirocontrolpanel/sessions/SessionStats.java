package com.ourgiant.kirocontrolpanel.sessions;

import java.time.Duration;
import java.util.List;

/**
 * Derives lightweight per-session statistics -- message counts by role, total words
 * exchanged, longest reply, session duration -- from a session's already-parsed transcript
 * messages. Computed on demand when a session is selected in the Sessions tab's detail pane
 * rather than persisted in the SQLite index: these are cheap to derive from data
 * {@link SessionManifestParser#parseTranscript} already produces, so there's no need to grow
 * the index schema (and force a rebuild) just to show them.
 */
final class SessionStats {

    private SessionStats() {
    }

    record Summary(int userMessageCount, int assistantMessageCount, int totalWordCount,
                    int longestReplyWordCount, Duration duration) {
    }

    static Summary summarize(List<TranscriptMessage> messages, Duration duration) {
        int userCount = 0;
        int assistantCount = 0;
        int totalWords = 0;
        int longestReplyWords = 0;
        for (TranscriptMessage message : messages) {
            int words = wordCount(message.text());
            totalWords += words;
            if ("user".equals(message.role())) {
                userCount++;
            } else if ("assistant".equals(message.role())) {
                assistantCount++;
                longestReplyWords = Math.max(longestReplyWords, words);
            }
        }
        return new Summary(userCount, assistantCount, totalWords, longestReplyWords, duration);
    }

    static String format(Summary summary) {
        int totalMessages = summary.userMessageCount() + summary.assistantMessageCount();
        return "Messages: " + totalMessages
            + " (" + summary.userMessageCount() + " you / " + summary.assistantMessageCount() + " Kiro)\n"
            + "Duration: " + formatDuration(summary.duration()) + "\n"
            + "Words exchanged: " + summary.totalWordCount() + "\n"
            + "Longest reply: " + summary.longestReplyWordCount() + " words";
    }

    private static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}

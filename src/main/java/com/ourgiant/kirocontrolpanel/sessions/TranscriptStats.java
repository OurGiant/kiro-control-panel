package com.ourgiant.kirocontrolpanel.sessions;

import java.util.List;

/**
 * The result of walking some suffix of a {@code <uuid>.jsonl} transcript,
 * starting at a given line ({@link SessionManifestParser#parseTranscript}).
 * Deliberately a delta, not a running total -- {@code SessionIndexService}
 * merges it into whatever it already has stored for a session, so an
 * already-indexed prefix of an append-only file is never re-read.
 */
public record TranscriptStats(
    int messageCount,
    List<String> filesTouched,
    List<String> reposTouched,
    String firstPromptText,
    long linesConsumed,
    List<TranscriptMessage> messages
) {
}

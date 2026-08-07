package com.ourgiant.kirocontrolpanel.sessions;

/**
 * One user or assistant text message extracted from a session transcript,
 * for feeding into the full-text search index. Deliberately excludes tool
 * call/result payloads -- see {@link SessionManifestParser} class Javadoc --
 * so the FTS index stays scoped to conversational content, not large,
 * low-signal tool output.
 */
public record TranscriptMessage(String messageId, String role, String text) {
}

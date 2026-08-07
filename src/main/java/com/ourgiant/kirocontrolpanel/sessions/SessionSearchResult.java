package com.ourgiant.kirocontrolpanel.sessions;

/**
 * One ranked full-text search match, as returned by
 * {@link SessionIndexService#search}. {@code snippet} is FTS5's own
 * {@code snippet()} output (the matched region with surrounding context,
 * pre-highlighted) -- see {@code SessionIndexSchema}'s
 * {@code session_messages_fts} definition for column ordering.
 */
public record SessionSearchResult(String sessionId, String role, String snippet, double rank) {
}

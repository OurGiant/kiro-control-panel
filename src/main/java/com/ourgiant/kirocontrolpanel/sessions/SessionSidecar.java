package com.ourgiant.kirocontrolpanel.sessions;

/**
 * The parsed subset of a kiro-cli {@code <uuid>.json} sidecar this app needs.
 * kiro-cli's own sidecar has more fields ({@code session_state} in particular,
 * with permissions/model-info/agent-name) -- everything not needed for the
 * Sessions tab's manifest is deliberately left unparsed rather than modeled.
 */
public record SessionSidecar(
    String sessionId,
    String cwd,
    long createdAtEpochMilli,
    long updatedAtEpochMilli,
    String title,
    String sessionCreatedReason
) {
}

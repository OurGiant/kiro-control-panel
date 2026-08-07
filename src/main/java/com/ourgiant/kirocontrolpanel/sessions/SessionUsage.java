package com.ourgiant.kirocontrolpanel.sessions;

/**
 * Credit and context-window usage for one session, parsed from the {@code <uuid>.json}
 * sidecar's {@code session_state.rts_model_state} and
 * {@code session_state.conversation_metadata.user_turn_metadatas} -- confirmed against
 * real seeded kiro-cli 2.13.0 data (issue #120 follow-up). {@code contextUsagePercentage}
 * and {@code contextWindowTokens} are nullable: both are {@code null} on a session that
 * hasn't completed a single turn yet (confirmed real -- most zero-turn sessions on a real
 * dev machine have a {@code null} {@code context_usage_percentage}, and some even have a
 * {@code null} {@code context_window_tokens} despite a non-null {@code model_info} object).
 */
public record SessionUsage(
    int turnCount,
    double totalCredits,
    Double contextUsagePercentage,
    Integer contextWindowTokens,
    String modelId
) {
}

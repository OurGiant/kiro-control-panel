package com.ourgiant.kirocontrolpanel.mcp;

import java.util.Set;

/**
 * Result of {@link McpRegistryStatusService#checkStatus()}. {@code httpStatusCode} is nullable --
 * a registry failure was detected in kiro-cli's output but an HTTP status couldn't be parsed out
 * of it (e.g. a timeout rather than an HTTP error response).
 * <p>
 * {@link #OK} deliberately covers three distinct real situations alike: not an enterprise/IDC
 * user, an enterprise user whose registry is currently reachable with nothing ignored, and
 * "couldn't determine" (e.g. kiro-cli isn't on PATH). Collapsing them is intentional -- the only
 * thing {@link McpPanel} acts on is "should a warning be shown," and a failed check should never
 * itself produce a false alarm.
 * <p>
 * {@code ignoredServerNames} (issue #139) is the set of locally-configured server names that a
 * <em>reachable</em> registry doesn't approve -- kiro-cli still runs, but silently drops these.
 * Always empty when {@code registryUnreachable} is true (that's a total-outage state, not a
 * partial one) or when the check couldn't determine anything.
 */
public record McpRegistryStatus(boolean registryUnreachable, Integer httpStatusCode, Set<String> ignoredServerNames) {
    public static final McpRegistryStatus OK = new McpRegistryStatus(false, null, Set.of());

    static McpRegistryStatus unreachable(Integer httpStatusCode) {
        return new McpRegistryStatus(true, httpStatusCode, Set.of());
    }

    static McpRegistryStatus reachable(Set<String> ignoredServerNames) {
        return new McpRegistryStatus(false, null, ignoredServerNames == null ? Set.of() : ignoredServerNames);
    }
}

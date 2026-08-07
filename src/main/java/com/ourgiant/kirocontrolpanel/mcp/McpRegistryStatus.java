package com.ourgiant.kirocontrolpanel.mcp;

/**
 * Result of {@link McpRegistryStatusService#checkStatus()}. {@code httpStatusCode} is nullable --
 * a registry failure was detected in kiro-cli's output but an HTTP status couldn't be parsed out
 * of it (e.g. a timeout rather than an HTTP error response).
 * <p>
 * {@link #OK} deliberately covers three distinct real situations alike: not an enterprise/IDC
 * user, an enterprise user whose registry is currently reachable, and "couldn't determine" (e.g.
 * kiro-cli isn't on PATH). Collapsing them is intentional -- the only thing {@link McpPanel} acts
 * on is "should a warning be shown," and a failed check should never itself produce a false alarm.
 */
public record McpRegistryStatus(boolean registryUnreachable, Integer httpStatusCode) {
    public static final McpRegistryStatus OK = new McpRegistryStatus(false, null);

    static McpRegistryStatus unreachable(Integer httpStatusCode) {
        return new McpRegistryStatus(true, httpStatusCode);
    }
}

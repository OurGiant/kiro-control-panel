package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRegistryStatusServiceTest {

    /** Real message captured by hand from `kiro-cli mcp list` (2.16.2) against a real
     * enterprise-governed account whose registry returned HTTP 403. */
    private static final String REAL_FAILURE_OUTPUT = """
        ❌ Failed to fetch registry data: Failed to fetch MCP registry: HTTP 403 Forbidden
        Registry contains invalid data. Contact your administrator.
        """;

    /** Shape captured by hand from `kiro-cli mcp list` (2.12.1) against a real enterprise-governed
     * account whose registry was reachable and only partially approving (issue #139) -- server
     * names below are synthetic stand-ins for the real ones observed, not the real org's data. */
    private static final String REAL_PARTIAL_APPROVAL_OUTPUT = """
        🤖 default:

          kiro_default
            ✓ atlassian
            ✓ bitbucket    npx

            Ignored (not in registry):
            ⚠ awslabs.cfn-mcp-server uvx (disabled)
            ⚠ awspricing   uvx (disabled)
            ⚠ awsknowledge  (disabled)

        🌍 global:

          dease
            ✓ atlassian
            ✓ bitbucket    npx

            Ignored (not in registry):
            ⚠ awspricing   uvx (disabled) [legacy]
            ⚠ awslabs.cfn-mcp-server uvx (disabled) [legacy]
            ⚠ awsknowledge  (disabled) [legacy]
        """;

    @Test
    void parsesTheRealCapturedFailureMessageWithItsHttpStatus() {
        McpRegistryStatus status = McpRegistryStatusService.parseListOutput(REAL_FAILURE_OUTPUT);

        assertTrue(status.registryUnreachable());
        assertEquals(403, status.httpStatusCode());
    }

    @Test
    void degradesToOkWhenNoFailureMarkerIsPresent() {
        String normalOutput = "AWS Documentation\nMock Test Server\n";

        McpRegistryStatus status = McpRegistryStatusService.parseListOutput(normalOutput);

        assertFalse(status.registryUnreachable());
        assertNull(status.httpStatusCode());
        assertTrue(status.ignoredServerNames().isEmpty());
    }

    @Test
    void degradesToOkOnEmptyOutput() {
        McpRegistryStatus status = McpRegistryStatusService.parseListOutput("");

        assertFalse(status.registryUnreachable());
        assertTrue(status.ignoredServerNames().isEmpty());
    }

    /** Issue #139: the real captured shape of a reachable registry that only approves a subset
     * of the locally-configured servers -- confirms names are pulled out of the "Ignored (not in
     * registry):" subsections, deduplicated across the multiple scope/agent blocks that repeat
     * the same names, and that approved ("✓") names are never included. */
    @Test
    void extractsIgnoredServerNamesFromARealPartialApprovalOutput() {
        McpRegistryStatus status = McpRegistryStatusService.parseListOutput(REAL_PARTIAL_APPROVAL_OUTPUT);

        assertFalse(status.registryUnreachable());
        assertNull(status.httpStatusCode());
        assertEquals(Set.of("awslabs.cfn-mcp-server", "awspricing", "awsknowledge"), status.ignoredServerNames());
    }

    @Test
    void stillFlagsUnreachableWhenTheFailureMarkerIsPresentButNoHttpStatusIsParseable() {
        String output = "❌ Failed to fetch registry data: request timed out\n";

        McpRegistryStatus status = McpRegistryStatusService.parseListOutput(output);

        assertTrue(status.registryUnreachable());
        assertNull(status.httpStatusCode());
    }

    /** Regression test: whether the binary is missing, or (as tested here) exists but isn't a
     * real kiro-cli, checkStatus must never throw -- it's a best-effort background check that
     * degrades quietly rather than ever surfacing a false alarm from an unexpected failure. */
    @Test
    void checkStatusNeverThrowsAndDegradesToOkWhenTheBinaryIsMissing() {
        McpRegistryStatus status = assertDoesNotThrow(() ->
            McpRegistryStatusService.checkStatus("kiro-cli-binary-that-does-not-exist-anywhere"));

        assertFalse(status.registryUnreachable());
    }
}

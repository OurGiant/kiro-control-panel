package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

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
    }

    @Test
    void degradesToOkOnEmptyOutput() {
        McpRegistryStatus status = McpRegistryStatusService.parseListOutput("");

        assertFalse(status.registryUnreachable());
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

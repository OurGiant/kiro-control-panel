package com.ourgiant.kirocontrolpanel.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiroUsageServiceTest {

    /** Real shape returned by kiro-cli 2.13.0's `_kiro.dev/commands/execute` "usage" command, captured by hand. */
    private static final String SAMPLE_DATA_JSON = """
        {
          "planName": "KIRO PRO+",
          "billingCycleReset": "2026-08-01",
          "overagesEnabled": false,
          "isEnterprise": true,
          "usageBreakdowns": [
            {
              "resourceType": "CREDIT",
              "displayName": "Credits",
              "used": 1980.8,
              "limit": 2000.0,
              "percentage": 99,
              "currentOverages": 0.0,
              "overageRate": 0.04,
              "overageCharges": 0.0,
              "currency": "USD",
              "hasLimit": true
            }
          ],
          "bonusCredits": [],
          "addOnCredits": [],
          "overageCapable": true
        }
        """;

    @Test
    void parsesRealResponseShape() throws Exception {
        JsonNode data = new ObjectMapper().readTree(SAMPLE_DATA_JSON);

        KiroUsageService.UsageSnapshot snapshot = KiroUsageService.parseSnapshot(data);

        assertEquals("KIRO PRO+", snapshot.planName());
        assertEquals("2026-08-01", snapshot.billingCycleReset());
        assertFalse(snapshot.overagesEnabled());
        assertEquals(1, snapshot.breakdowns().size());

        KiroUsageService.UsageBreakdown credits = snapshot.breakdowns().get(0);
        assertEquals("CREDIT", credits.resourceType());
        assertEquals("Credits", credits.displayName());
        assertEquals(1980.8, credits.used());
        assertEquals(2000.0, credits.limit());
        assertEquals(99, credits.percentage());
        assertTrue(credits.hasLimit());
        assertEquals("USD", credits.currency());
    }

    @Test
    void missingUsageBreakdownsYieldsEmptyList() throws Exception {
        JsonNode data = new ObjectMapper().readTree("""
            {"planName": "Free", "billingCycleReset": "2026-08-01", "overagesEnabled": false}
            """);

        KiroUsageService.UsageSnapshot snapshot = KiroUsageService.parseSnapshot(data);

        assertEquals("Free", snapshot.planName());
        assertEquals(List.of(), snapshot.breakdowns());
    }

    @Test
    void fetchUsageThrowsWhenBinaryIsNotOnPath() {
        KiroUsageService.UsageFetchException exception = assertThrows(
            KiroUsageService.UsageFetchException.class,
            () -> KiroUsageService.fetchUsage("kiro-cli-binary-that-does-not-exist-anywhere"));

        assertTrue(exception.getMessage().contains("isn't on your PATH"));
    }

    /** Regression test for issue #124: a throwaway "(no prompt)" session accumulated in
     * ~/.kiro/sessions/cli/ on every single usage check, since ACP's session/new has no
     * ephemeral-session concept. Cleanup is deliberately best-effort (see the method's own
     * Javadoc) -- confirms a cleanup failure never propagates as an exception of its own,
     * since that would turn "couldn't tidy up" into a worse failure than the original fetch. */
    @Test
    void deleteThrowawaySessionNeverThrowsEvenWhenTheBinaryIsMissing() {
        assertDoesNotThrow(() ->
            KiroUsageService.deleteThrowawaySession("kiro-cli-binary-that-does-not-exist-anywhere", "some-session-id"));
    }
}

package com.ourgiant.kirocontrolpanel.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    // -- V3 _kiro/account/getUsage (issue #149) --

    @Test
    void v3SuccessfulResultIsParsedWithTheSameShapeAsV2() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode result = mapper.createObjectNode()
            .put("success", true)
            .put("message", "Plan: KIRO PRO+ | 1 usage breakdowns")
            .set("data", mapper.readTree(SAMPLE_DATA_JSON));

        KiroUsageService.UsageSnapshot snapshot = KiroUsageService.parseV3Result(result);
        assertEquals("KIRO PRO+", snapshot.planName());
        assertEquals("2026-08-01", snapshot.billingCycleReset());
        assertEquals(1980.8, snapshot.breakdowns().get(0).used(), 1e-9);
    }

    @Test
    void v3AdminManagedPlanIsADistinctAnswerNotAFailure() throws Exception {
        JsonNode result = new ObjectMapper().readTree(
            "{\"success\": true, \"message\": \"Your plan is managed by admin\"}");
        KiroUsageService.AdminManagedPlanException e = assertThrows(
            KiroUsageService.AdminManagedPlanException.class, () -> KiroUsageService.parseV3Result(result));
        assertEquals("Your plan is managed by admin", e.getMessage());
    }

    @Test
    void v3FailureCarriesKiroCliMessageSoTheCallerCanFallBack() throws Exception {
        JsonNode result = new ObjectMapper().readTree(
            "{\"success\": false, \"message\": \"Failed to retrieve usage information: boom\"}");
        KiroUsageService.UsageFetchException e = assertThrows(
            KiroUsageService.UsageFetchException.class, () -> KiroUsageService.parseV3Result(result));
        assertFalse(e instanceof KiroUsageService.AdminManagedPlanException);
        assertTrue(e.getMessage().contains("boom"));
    }
}

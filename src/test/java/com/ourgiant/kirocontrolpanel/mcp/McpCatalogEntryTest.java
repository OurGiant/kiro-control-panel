package com.ourgiant.kirocontrolpanel.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCatalogEntryTest {

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();

    @Test
    void deserializesLocalEntry() throws Exception {
        String json = """
            {
              "slug": "aws-docs",
              "name": "AWS Documentation",
              "description": "Access to AWS documentation, search capabilities, and content recommendations",
              "requirement": "Requires UV Installed",
              "config": {
                "command": "uvx",
                "args": ["awslabs.aws-documentation-mcp-server@latest"],
                "env": {"FASTMCP_LOG_LEVEL": "ERROR"},
                "disabled": false,
                "autoApprove": []
              }
            }
            """;

        McpCatalogEntry entry = mapper.readValue(json, McpCatalogEntry.class);

        assertEquals("aws-docs", entry.getSlug());
        assertEquals("AWS Documentation", entry.getName());
        assertEquals("Requires UV Installed", entry.getRequirement());
        assertFalse(entry.getConfig().isRemote());
        assertEquals("uvx", entry.getConfig().getCommand());
        assertEquals("ERROR", entry.getConfig().getEnv().get("FASTMCP_LOG_LEVEL"));
        assertFalse(entry.getConfig().isDisabled());
    }

    @Test
    void deserializesRemoteEntryWithNoRequirement() throws Exception {
        String json = """
            {
              "slug": "amplitude-mcp",
              "name": "Amplitude",
              "description": "Interact with Amplitude AI-Powered product data.",
              "requirement": null,
              "config": {"url": "https://mcp.amplitude.com/mcp", "disabled": false, "autoApprove": []}
            }
            """;

        McpCatalogEntry entry = mapper.readValue(json, McpCatalogEntry.class);

        assertNull(entry.getRequirement());
        assertTrue(entry.getConfig().isRemote());
        assertEquals("https://mcp.amplitude.com/mcp", entry.getConfig().getUrl());
    }

    @Test
    void deserializesDisabledEntryWithPlaceholderEnv() throws Exception {
        String json = """
            {
              "slug": "crowdstrike-falcon-mcp",
              "name": "CrowdStrike",
              "description": "Connect AI agents to CrowdStrike Falcon for security analysis",
              "requirement": "Requires UV Installed",
              "config": {
                "command": "uvx",
                "args": ["falcon-mcp"],
                "env": {"FALCON_CLIENT_ID": "${FALCON_CLIENT_ID}", "FALCON_CLIENT_SECRET": "${FALCON_CLIENT_SECRET}"},
                "disabled": true,
                "autoApprove": []
              }
            }
            """;

        McpCatalogEntry entry = mapper.readValue(json, McpCatalogEntry.class);

        assertTrue(entry.getConfig().isDisabled());
        assertEquals("${FALCON_CLIENT_ID}", entry.getConfig().getEnv().get("FALCON_CLIENT_ID"));
    }

    private McpCatalogEntry entryNamed(String name, String description) {
        McpCatalogEntry entry = new McpCatalogEntry();
        entry.setName(name);
        entry.setDescription(description);
        return entry;
    }

    @Test
    void filterWithBlankQueryReturnsAllEntries() {
        List<McpCatalogEntry> entries = List.of(
            entryNamed("AWS Documentation", "docs"),
            entryNamed("GitHub", "repos"));

        assertEquals(2, McpCatalogEntry.filter(entries, "").size());
        assertEquals(2, McpCatalogEntry.filter(entries, null).size());
    }

    @Test
    void filterMatchesNameCaseInsensitively() {
        List<McpCatalogEntry> entries = List.of(
            entryNamed("AWS Documentation", "docs"),
            entryNamed("GitHub", "repos"));

        List<McpCatalogEntry> result = McpCatalogEntry.filter(entries, "github");

        assertEquals(1, result.size());
        assertEquals("GitHub", result.get(0).getName());
    }

    @Test
    void filterMatchesDescription() {
        List<McpCatalogEntry> entries = List.of(
            entryNamed("AWS Documentation", "Access to AWS documentation and search"),
            entryNamed("GitHub", "Interact with GitHub repositories"));

        List<McpCatalogEntry> result = McpCatalogEntry.filter(entries, "repositories");

        assertEquals(1, result.size());
        assertEquals("GitHub", result.get(0).getName());
    }

    @Test
    void filterWithNoMatchesReturnsEmptyList() {
        List<McpCatalogEntry> entries = List.of(entryNamed("AWS Documentation", "docs"));

        assertTrue(McpCatalogEntry.filter(entries, "nonexistent").isEmpty());
    }
}

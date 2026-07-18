package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCatalogServiceTest {

    @Test
    void loadsBundledCatalogWithSaneEntries() {
        List<McpCatalogEntry> entries = new McpCatalogService().load();

        assertFalse(entries.isEmpty());

        Set<String> slugs = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (McpCatalogEntry entry : entries) {
            assertTrue(slugs.add(entry.getSlug()), "duplicate slug: " + entry.getSlug());
            assertTrue(names.add(entry.getName()), "duplicate name: " + entry.getName());
            assertFalse(entry.getName() == null || entry.getName().isBlank(), "blank name for slug " + entry.getSlug());
            McpServerConfig config = entry.getConfig();
            boolean hasCommand = config.getCommand() != null && !config.getCommand().isBlank();
            boolean hasUrl = config.getUrl() != null && !config.getUrl().isBlank();
            assertTrue(hasCommand || hasUrl, "neither command nor url set for " + entry.getName());
        }
    }

    @Test
    void loadReturnsSameCachedInstanceOnSecondCall() {
        McpCatalogService service = new McpCatalogService();

        List<McpCatalogEntry> first = service.load();
        List<McpCatalogEntry> second = service.load();

        assertTrue(first == second);
    }

    @Test
    void loadOfMissingResourceReturnsEmptyListWithoutThrowing() {
        McpCatalogService service = new McpCatalogService("/does-not-exist.json");

        assertTrue(service.load().isEmpty());
    }
}

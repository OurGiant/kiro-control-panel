package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void parseEntriesParsesValidJson() throws IOException {
        String json = """
            [
              {"slug": "aws-docs", "name": "AWS Documentation", "description": "Access AWS docs",
               "requirement": null, "config": {"command": "uvx", "args": ["mcp-server"], "disabled": false}}
            ]
            """;
        McpCatalogService service = new McpCatalogService();

        List<McpCatalogEntry> entries = service.parseEntries(toStream(json));

        assertEquals(1, entries.size());
        assertEquals("aws-docs", entries.get(0).getSlug());
        assertEquals("AWS Documentation", entries.get(0).getName());
        assertEquals("uvx", entries.get(0).getConfig().getCommand());
    }

    @Test
    void parseEntriesThrowsOnMalformedJson() {
        McpCatalogService service = new McpCatalogService();

        assertThrows(IOException.class, () -> service.parseEntries(toStream("{ not valid json")));
    }

    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}

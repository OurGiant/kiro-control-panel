package com.ourgiant.kirocontrolpanel.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One entry in the bundled MCP server catalog (generated from kiro.dev's
 * known-servers list by tools/generate-mcp-catalog.py). {@code slug} is the
 * catalog's own stable identifier -- kiro.dev's deep-link "name" param,
 * which is not a mechanical slug of {@code name} (e.g. "AWS Documentation"
 * -> "aws-docs", "Atlassian" -> "atlassian-rovo") -- so it exists purely
 * for dedupe/diffing across regenerations, never shown in the UI or used
 * as the suggested mcp.json key. {@code name} is what's shown and what
 * pre-fills the "Add MCP Server" form.
 */
public class McpCatalogEntry {

    private String slug;
    private String name;
    private String description;
    private String requirement;
    private McpServerConfig config;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public McpServerConfig getConfig() {
        return config;
    }

    public void setConfig(McpServerConfig config) {
        this.config = config;
    }

    /**
     * Case-insensitive substring match against name and description. Empty
     * or blank query matches everything.
     */
    static List<McpCatalogEntry> filter(List<McpCatalogEntry> entries, String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(entries);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<McpCatalogEntry> result = new ArrayList<>();
        for (McpCatalogEntry entry : entries) {
            String name = entry.getName() == null ? "" : entry.getName().toLowerCase(Locale.ROOT);
            String description = entry.getDescription() == null ? "" : entry.getDescription().toLowerCase(Locale.ROOT);
            if (name.contains(needle) || description.contains(needle)) {
                result.add(entry);
            }
        }
        return result;
    }
}

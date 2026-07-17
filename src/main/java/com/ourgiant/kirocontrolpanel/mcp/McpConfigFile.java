package com.ourgiant.kirocontrolpanel.mcp;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level shape of mcp.json: {@code {"mcpServers": {...}}}. Unknown
 * top-level keys round-trip through {@code extra}.
 */
public class McpConfigFile {

    private Map<String, McpServerConfig> mcpServers = new LinkedHashMap<>();
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonProperty("mcpServers")
    public Map<String, McpServerConfig> getMcpServers() {
        return mcpServers;
    }

    @JsonProperty("mcpServers")
    public void setMcpServers(Map<String, McpServerConfig> mcpServers) {
        this.mcpServers = mcpServers != null ? mcpServers : new LinkedHashMap<>();
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        extra.put(key, value);
    }
}

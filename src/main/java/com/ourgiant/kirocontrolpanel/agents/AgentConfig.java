package com.ourgiant.kirocontrolpanel.agents;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One agent config file under .kiro/agents/ (workspace) or ~/.kiro/agents/
 * (global) -- filename minus ".json" is the agent's identity by default,
 * per Kiro's own schema, so unlike mcp.json/hooks this is one file per
 * item, not a map inside one file. Only the fields with dedicated Form UI
 * (see AgentFormPanel) are typed; everything else Kiro's schema allows
 * (name, mcpServers, toolAliases, toolsSettings, resources, hooks) round
 * trips losslessly through {@code extra} so a Form-tab-only edit never
 * destroys config only reachable via the Raw JSON tab.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentConfig {

    private final Path filePath;
    private final Path workspaceRoot;

    private String description;
    private String prompt;
    private String model;
    private String keyboardShortcut;
    private String welcomeMessage;
    private List<String> tools;
    private List<String> allowedTools;
    private Boolean includeMcpJson;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    /**
     * {@code filePath}/{@code workspaceRoot} may be null for transient,
     * identity-less instances (e.g. built from the Raw JSON tab) that are
     * never persisted directly -- only used via populateFrom/field-copy.
     */
    public AgentConfig(Path filePath, Path workspaceRoot) {
        this.filePath = filePath;
        this.workspaceRoot = workspaceRoot;
    }

    @JsonIgnore
    public Path getFilePath() {
        return filePath;
    }

    @JsonIgnore
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    @JsonIgnore
    public boolean isGlobal() {
        return workspaceRoot == null;
    }

    @JsonIgnore
    public String getFileName() {
        return filePath.getFileName().toString();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getKeyboardShortcut() {
        return keyboardShortcut;
    }

    public void setKeyboardShortcut(String keyboardShortcut) {
        this.keyboardShortcut = keyboardShortcut;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }

    public List<String> getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools;
    }

    public Boolean getIncludeMcpJson() {
        return includeMcpJson;
    }

    public void setIncludeMcpJson(Boolean includeMcpJson) {
        this.includeMcpJson = includeMcpJson;
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

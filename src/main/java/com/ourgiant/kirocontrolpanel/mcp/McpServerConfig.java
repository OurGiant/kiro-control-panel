package com.ourgiant.kirocontrolpanel.mcp;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entry under "mcpServers" in mcp.json. Covers both local servers
 * (command/args/env) and remote servers (url/headers/oauth/oauthScopes);
 * Kiro's schema doesn't discriminate between the two beyond which fields
 * are present, so this is intentionally one flat POJO rather than a type
 * hierarchy. Unknown fields round-trip through {@code extra} instead of
 * being dropped, so editing a server here never silently destroys config
 * this tool doesn't model yet.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpServerConfig {

    private String command;
    private List<String> args;
    private Map<String, String> env;

    private String url;
    private Map<String, String> headers;
    private Map<String, Object> oauth;
    private List<String> oauthScopes;

    private boolean disabled;
    private List<String> autoApprove;
    private List<String> disabledTools;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    public boolean isRemote() {
        return url != null && !url.isBlank();
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getOauth() {
        return oauth;
    }

    public void setOauth(Map<String, Object> oauth) {
        this.oauth = oauth;
    }

    public List<String> getOauthScopes() {
        return oauthScopes;
    }

    public void setOauthScopes(List<String> oauthScopes) {
        this.oauthScopes = oauthScopes;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public List<String> getAutoApprove() {
        return autoApprove;
    }

    public void setAutoApprove(List<String> autoApprove) {
        this.autoApprove = autoApprove;
    }

    public List<String> getDisabledTools() {
        return disabledTools;
    }

    public void setDisabledTools(List<String> disabledTools) {
        this.disabledTools = disabledTools;
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

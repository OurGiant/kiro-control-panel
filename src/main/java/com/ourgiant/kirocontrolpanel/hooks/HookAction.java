package com.ourgiant.kirocontrolpanel.hooks;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A hook's action: either a shell command (stdout added to agent context on
 * success) or an agent prompt (consumes credits, per Kiro's docs). Unknown
 * fields round-trip through {@code extra}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HookAction {
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_AGENT = "agent";

    private String type;
    private String command;
    private String prompt;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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

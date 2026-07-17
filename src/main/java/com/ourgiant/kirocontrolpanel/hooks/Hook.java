package com.ourgiant.kirocontrolpanel.hooks;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One entry in a hooks file's "hooks" array. {@code trigger} is one of
 * Kiro's known event names (prompt_submit, agent_stop, pre_tool_use,
 * post_tool_use, file_create, file_save, file_delete, pre_task_execution,
 * post_task_execution, manual_trigger); {@code matcher} scope depends on
 * the trigger (tool name for *_tool_use, file glob for file_*, unused by
 * the rest). Unknown fields round-trip through {@code extra}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Hook {
    private String name;
    private String trigger;
    private String matcher;
    private HookAction action;
    private Integer timeout;
    private boolean enabled = true;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getMatcher() {
        return matcher;
    }

    public void setMatcher(String matcher) {
        this.matcher = matcher;
    }

    public HookAction getAction() {
        return action;
    }

    public void setAction(HookAction action) {
        this.action = action;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

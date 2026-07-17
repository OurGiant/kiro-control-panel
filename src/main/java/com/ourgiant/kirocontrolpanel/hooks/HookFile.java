package com.ourgiant.kirocontrolpanel.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * On-disk shape of one .kiro/hooks/*.json file: {@code {"version":"v1","hooks":[...]}}.
 * A workspace can have multiple such files.
 */
public class HookFile {
    private String version = "v1";
    private List<Hook> hooks = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<Hook> getHooks() {
        return hooks;
    }

    public void setHooks(List<Hook> hooks) {
        this.hooks = hooks != null ? hooks : new ArrayList<>();
    }
}

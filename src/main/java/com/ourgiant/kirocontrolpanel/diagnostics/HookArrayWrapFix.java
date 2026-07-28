package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.hooks.Hook;
import com.ourgiant.kirocontrolpanel.hooks.HookFile;
import com.ourgiant.kirocontrolpanel.hooks.HookService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Repairs a hooks file whose top-level JSON is a bare array instead of the
 * {@code {"version":"v1","hooks":[...]}} shape Kiro expects, by wrapping the
 * already-parsed hooks in the correct envelope and rewriting the file via
 * the same {@link HookService#save} every other hooks write in this app uses.
 */
public final class HookArrayWrapFix implements Fix {
    private final Path path;
    private final List<Hook> hooks;
    private final HookService hookService;

    public HookArrayWrapFix(Path path, List<Hook> hooks, HookService hookService) {
        this.path = path;
        this.hooks = hooks;
        this.hookService = hookService;
    }

    @Override
    public String previewText() {
        return "Wrap " + path.getFileName() + "'s hook array in {\"version\":\"v1\",\"hooks\":[...]}";
    }

    @Override
    public void apply() throws IOException {
        HookFile hookFile = new HookFile();
        hookFile.setHooks(hooks);
        hookService.save(path, hookFile);
    }
}

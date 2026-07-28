package com.ourgiant.kirocontrolpanel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * A scope a Kiro-managed resource (steering doc, MCP server, skill, hook)
 * can live in: the global {@code ~/.kiro} root, or a pinned workspace's
 * {@code .kiro} root. {@code workspaceRoot} is null for Global.
 */
public record WorkspaceScope(String label, Path workspaceRoot) {

    public static WorkspaceScope global() {
        return new WorkspaceScope("Global", null);
    }

    /** One scope per workspace pinned in {@code preferences}, in pinned order -- does not include Global. */
    public static List<WorkspaceScope> pinnedWorkspaces(AppPreferences preferences) {
        List<WorkspaceScope> scopes = new ArrayList<>();
        for (String workspace : preferences.getWorkspaces()) {
            scopes.add(new WorkspaceScope(workspace, Paths.get(workspace)));
        }
        return scopes;
    }

    public boolean isGlobal() {
        return workspaceRoot == null;
    }

    @Override
    public String toString() {
        return label;
    }
}

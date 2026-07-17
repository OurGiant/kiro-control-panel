package com.ourgiant.kirocontrolpanel;

import java.nio.file.Path;

/**
 * A scope a Kiro-managed resource (steering doc, MCP server, skill, hook)
 * can live in: the global {@code ~/.kiro} root, or a pinned workspace's
 * {@code .kiro} root. {@code workspaceRoot} is null for Global.
 */
public record WorkspaceScope(String label, Path workspaceRoot) {

    public static WorkspaceScope global() {
        return new WorkspaceScope("Global", null);
    }

    public boolean isGlobal() {
        return workspaceRoot == null;
    }

    @Override
    public String toString() {
        return label;
    }
}

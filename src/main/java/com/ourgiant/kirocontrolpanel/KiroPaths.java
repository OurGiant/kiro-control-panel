package com.ourgiant.kirocontrolpanel;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the on-disk locations Kiro itself reads: the global {@code .kiro}
 * root (honoring {@code KIRO_HOME} the same way Kiro does) and per-workspace
 * {@code .kiro} roots.
 */
public final class KiroPaths {

    private KiroPaths() {
    }

    public static Path globalKiroHome() {
        String override = System.getenv("KIRO_HOME");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".kiro");
    }

    public static Path globalMcpConfig() {
        return globalKiroHome().resolve("settings").resolve("mcp.json");
    }

    public static Path globalSteeringDir() {
        return globalKiroHome().resolve("steering");
    }

    public static Path globalSkillsDir() {
        return globalKiroHome().resolve("skills");
    }

    public static Path globalAgentsDir() {
        return globalKiroHome().resolve("agents");
    }

    public static Path workspaceKiroDir(Path workspaceRoot) {
        return workspaceRoot.resolve(".kiro");
    }

    public static Path workspaceMcpConfig(Path workspaceRoot) {
        return workspaceKiroDir(workspaceRoot).resolve("settings").resolve("mcp.json");
    }

    public static Path workspaceSteeringDir(Path workspaceRoot) {
        return workspaceKiroDir(workspaceRoot).resolve("steering");
    }

    public static Path workspaceSkillsDir(Path workspaceRoot) {
        return workspaceKiroDir(workspaceRoot).resolve("skills");
    }

    public static Path workspaceAgentsDir(Path workspaceRoot) {
        return workspaceKiroDir(workspaceRoot).resolve("agents");
    }

    public static Path workspaceHooksDir(Path workspaceRoot) {
        return workspaceKiroDir(workspaceRoot).resolve("hooks");
    }
}

package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.agents.AgentConfig;
import com.ourgiant.kirocontrolpanel.agents.AgentEditDialog;
import com.ourgiant.kirocontrolpanel.agents.AgentService;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSurface;
import com.ourgiant.kirocontrolpanel.hooks.Hook;
import com.ourgiant.kirocontrolpanel.hooks.HookEditDialog;
import com.ourgiant.kirocontrolpanel.hooks.HookEntry;
import com.ourgiant.kirocontrolpanel.hooks.HookService;
import com.ourgiant.kirocontrolpanel.skills.Skill;
import com.ourgiant.kirocontrolpanel.skills.SkillEditorDialog;
import com.ourgiant.kirocontrolpanel.skills.SkillService;
import com.ourgiant.kirocontrolpanel.steering.SteeringDoc;
import com.ourgiant.kirocontrolpanel.steering.SteeringEditorDialog;
import com.ourgiant.kirocontrolpanel.steering.SteeringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Opens the app's own in-app editor for a given file, instead of shelling
 * out to an external OS default application ({@code DesktopUtils.openFile},
 * removed for exactly this reason -- see issue #82). Every path here ends
 * up going through the same save machinery ({@code SelfWriteTracker}/
 * {@code GitAutoCommitter}/{@code ChangeLogService}) every other in-app
 * edit already uses, instead of a process this app never sees.
 * <p>
 * Steering/Skills/Agents are naturally one-file-per-item, so a successful
 * structured load opens that surface's real editor dialog. MCP/Hooks are
 * not -- one file can hold many servers/hooks -- so they reuse the same
 * raw-JSON fallback (@link RawJsonEditorDialog} the MCP/Hooks panels
 * themselves already fall back to for "the form doesn't cover this."
 * Anything that fails to load structurally (an {@code INVALID} finding/
 * change-log entry) falls back the same way.
 */
public final class InAppFileEditorLauncher {
    private static final Logger logger = LoggerFactory.getLogger(InAppFileEditorLauncher.class);

    private static final String MCP_DEFAULT_CONTENT = "{\n  \"mcpServers\": {}\n}\n";
    private static final String HOOKS_DEFAULT_CONTENT = "{\n  \"version\": \"v1\",\n  \"hooks\": []\n}\n";

    private InAppFileEditorLauncher() {
    }

    public static void open(Frame parent, KiroSurface surface, WorkspaceScope scope, Path path) {
        switch (surface) {
            case STEERING -> openSteering(parent, scope, path);
            case SKILLS -> openSkill(parent, scope, path);
            case AGENTS -> openAgent(parent, scope, path);
            case HOOKS -> openHook(parent, scope, path);
            case MCP -> openRawJson(parent, path, MCP_DEFAULT_CONTENT);
        }
    }

    private static void openSteering(Frame parent, WorkspaceScope scope, Path path) {
        SteeringService service = new SteeringService();
        try {
            SteeringDoc doc = service.load(path, scope.isGlobal() ? null : scope.workspaceRoot());
            new SteeringEditorDialog(parent, service, doc).setVisible(true);
        } catch (IOException | RuntimeException e) {
            logger.warn("Steering doc {} failed to load structurally, falling back to raw editor", path, e);
            openRawText(parent, path);
        }
    }

    private static void openSkill(Frame parent, WorkspaceScope scope, Path path) {
        if (!"SKILL.md".equals(path.getFileName().toString())) {
            // A loose .md file directly in skills/ (pre-#78-fix) has no valid skill directory to load.
            openRawText(parent, path);
            return;
        }
        SkillService service = new SkillService();
        try {
            Skill skill = service.load(path.getParent(), scope.isGlobal() ? null : scope.workspaceRoot());
            new SkillEditorDialog(parent, service, skill).setVisible(true);
        } catch (IOException | RuntimeException e) {
            logger.warn("Skill {} failed to load structurally, falling back to raw editor", path, e);
            openRawText(parent, path);
        }
    }

    private static void openAgent(Frame parent, WorkspaceScope scope, Path path) {
        AgentService service = new AgentService();
        AgentConfig config;
        try {
            config = service.load(path, scope.isGlobal() ? null : scope.workspaceRoot());
        } catch (IOException e) {
            logger.warn("Agent {} failed to load structurally, falling back to raw editor", path, e);
            openRawJson(parent, path, "");
            return;
        }
        AgentEditDialog dialog = new AgentEditDialog(parent, config, false);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            try {
                service.save(config);
            } catch (IOException e) {
                logger.error("Failed to save agent config {}", path, e);
                JOptionPane.showMessageDialog(parent, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void openHook(Frame parent, WorkspaceScope scope, Path path) {
        if (scope.isGlobal()) {
            // Hooks are workspace-only; shouldn't happen, but there's no structured path here regardless.
            openRawJson(parent, path, HOOKS_DEFAULT_CONTENT);
            return;
        }
        HookService service = new HookService();
        Optional<HookEntry> entry = resolveHookEntry(service, scope.workspaceRoot(), path);
        if (entry.isEmpty()) {
            openRawJson(parent, path, HOOKS_DEFAULT_CONTENT);
            return;
        }
        Hook hook = entry.get().getHook();
        HookEditDialog dialog = new HookEditDialog(parent, hook, false);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            try {
                service.save(entry.get().getFilePath(), entry.get().getFile());
            } catch (IOException e) {
                logger.error("Failed to save hook {}", path, e);
                JOptionPane.showMessageDialog(parent, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Resolves the single {@link HookEntry} stored at {@code path}, since one hooks file can
     * hold multiple entries and a bare {@code Path} alone can't disambiguate which one a
     * finding/change-log entry meant. Empty if zero or more than one hook lives in that file
     * -- callers fall back to the raw-file editor in that case. Package-private, for tests.
     */
    static Optional<HookEntry> resolveHookEntry(HookService service, Path workspaceRoot, Path path) {
        List<HookEntry> matches = service.listWorkspace(workspaceRoot).stream()
            .filter(e -> e.getFilePath().equals(path))
            .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static void openRawJson(Frame parent, Path path, String defaultContentIfMissing) {
        new RawJsonEditorDialog(parent, "Edit: " + path, path, defaultContentIfMissing).setVisible(true);
    }

    private static void openRawText(Frame parent, Path path) {
        new RawJsonEditorDialog(parent, "Edit: " + path, path, "", false).setVisible(true);
    }
}

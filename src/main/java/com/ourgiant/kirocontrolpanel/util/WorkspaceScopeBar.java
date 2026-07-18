package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared "Scope: [Global v] [Add Workspace...] [Remove Workspace]" bar used
 * by any panel whose resource is scoped Global vs. a pinned workspace (MCP
 * servers, steering docs, skills). Backed by {@link AppPreferences}' pinned
 * workspace list so all panels share the same set.
 */
public class WorkspaceScopeBar extends JPanel {

    private final AppPreferences preferences;
    private final boolean includeGlobal;
    private final DefaultComboBoxModel<WorkspaceScope> scopeModel = new DefaultComboBoxModel<>();
    private final JComboBox<WorkspaceScope> scopeCombo = new JComboBox<>(scopeModel);
    private final JButton removeWorkspaceButton;
    private final List<Consumer<WorkspaceScope>> listeners = new ArrayList<>();

    public WorkspaceScopeBar(AppPreferences preferences) {
        this(preferences, true);
    }

    /**
     * @param includeGlobal false for resources Kiro only supports at the
     *                      workspace level (e.g. hooks) — the bar then only
     *                      offers pinned workspaces, no Global entry.
     */
    public WorkspaceScopeBar(AppPreferences preferences, boolean includeGlobal) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        this.preferences = preferences;
        this.includeGlobal = includeGlobal;

        add(new JLabel("Scope:"));
        add(scopeCombo);

        JButton addWorkspaceButton = new JButton("Add Workspace...");
        addWorkspaceButton.addActionListener(e -> onAddWorkspace());
        add(addWorkspaceButton);

        removeWorkspaceButton = new JButton("Remove Workspace");
        removeWorkspaceButton.addActionListener(e -> onRemoveWorkspace());
        add(removeWorkspaceButton);

        scopeCombo.addActionListener(e -> fireScopeChanged());
        reload();
        WorkspaceRegistry.addListener(this::reload);
    }

    public void addScopeChangeListener(Consumer<WorkspaceScope> listener) {
        listeners.add(listener);
    }

    public WorkspaceScope getSelectedScope() {
        return (WorkspaceScope) scopeCombo.getSelectedItem();
    }

    /** Package-private, for tests: the scope bar's current dropdown contents. */
    List<WorkspaceScope> getAvailableScopes() {
        List<WorkspaceScope> scopes = new ArrayList<>();
        for (int i = 0; i < scopeModel.getSize(); i++) {
            scopes.add(scopeModel.getElementAt(i));
        }
        return scopes;
    }

    /** Package-private, for tests: select a scope already present in the dropdown. */
    void selectScope(WorkspaceScope scope) {
        scopeCombo.setSelectedItem(scope);
    }

    public void reload() {
        Object previouslySelected = scopeCombo.getSelectedItem();
        scopeModel.removeAllElements();
        if (includeGlobal) {
            scopeModel.addElement(WorkspaceScope.global());
        }
        for (String workspace : preferences.getWorkspaces()) {
            scopeModel.addElement(new WorkspaceScope(workspace, Paths.get(workspace)));
        }
        if (previouslySelected != null) {
            for (int i = 0; i < scopeModel.getSize(); i++) {
                if (scopeModel.getElementAt(i).equals(previouslySelected)) {
                    scopeCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (scopeModel.getSize() > 0) {
            scopeCombo.setSelectedIndex(0);
        }
    }

    private void fireScopeChanged() {
        WorkspaceScope scope = getSelectedScope();
        removeWorkspaceButton.setEnabled(scope != null && !scope.isGlobal());
        for (Consumer<WorkspaceScope> listener : listeners) {
            listener.accept(scope);
        }
    }

    private void onAddWorkspace() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Workspace Root");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        preferences.addWorkspace(path);
        // Reloads every WorkspaceScopeBar instance app-wide (including this one) via the
        // registry, rather than calling reload() directly — otherwise sibling panels never
        // learn a workspace was pinned until something else happens to trigger their reload.
        WorkspaceRegistry.notifyChanged();
        for (int i = 0; i < scopeModel.getSize(); i++) {
            if (path.equals(scopeModel.getElementAt(i).label())) {
                scopeCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void onRemoveWorkspace() {
        WorkspaceScope scope = getSelectedScope();
        if (scope == null || scope.isGlobal()) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove workspace \"" + scope.label() + "\" from the pinned list? Its files are not touched.",
            "Remove Workspace",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        preferences.removeWorkspace(scope.label());
        WorkspaceRegistry.notifyChanged();
    }
}

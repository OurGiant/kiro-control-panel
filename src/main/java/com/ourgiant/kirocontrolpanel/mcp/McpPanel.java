package com.ourgiant.kirocontrolpanel.mcp;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.RawJsonEditorDialog;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import com.ourgiant.kirocontrolpanel.util.WorkspaceScopeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * MCP servers tab: lists mcpServers from the current scope's mcp.json and
 * lets the user add/edit/enable/disable/remove entries, or drop to a raw
 * JSON editor for anything the structured form doesn't cover.
 */
public class McpPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(McpPanel.class);

    private final McpConfigService configService = new McpConfigService();
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final McpServerTableModel tableModel = new McpServerTableModel();
    private final JTable table = new JTable(tableModel);

    private JButton editButton;
    private JButton removeButton;
    private JButton toggleEnabledButton;
    private JButton revealButton;

    public McpPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.watcher = watcher;
        watcher.addListener(this::reloadTable);

        scopeBar = new WorkspaceScopeBar(preferences);
        scopeBar.addScopeChangeListener(scope -> reloadTable());
        add(scopeBar, BorderLayout.NORTH);

        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createSideButtons(), BorderLayout.EAST);

        reloadTable();
    }

    private JPanel createSideButtons() {
        JButton addButton = new JButton("Add...");
        addButton.setMnemonic(KeyEvent.VK_A);
        addButton.addActionListener(e -> onAdd());

        JButton browseCatalogButton = new JButton("Browse Catalog...");
        browseCatalogButton.addActionListener(e -> onBrowseCatalog());

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        toggleEnabledButton = new JButton("Enable/Disable");
        toggleEnabledButton.setMnemonic(KeyEvent.VK_T);
        toggleEnabledButton.addActionListener(e -> onToggleEnabled());

        removeButton = new JButton("Remove");
        removeButton.setMnemonic(KeyEvent.VK_R);
        removeButton.addActionListener(e -> onRemove());

        JButton rawJsonButton = new JButton("Edit Raw JSON...");
        rawJsonButton.addActionListener(e -> onEditRawJson());

        revealButton = new JButton("Reveal File...");
        revealButton.addActionListener(e -> onReveal());

        return SwingLayoutUtils.createVerticalButtonPanel(
            addButton, browseCatalogButton, editButton, toggleEnabledButton, removeButton, rawJsonButton, revealButton);
    }

    private WorkspaceScope currentScope() {
        return scopeBar.getSelectedScope();
    }

    private Path currentConfigPath() {
        WorkspaceScope scope = currentScope();
        if (scope == null) {
            return null;
        }
        return scope.isGlobal() ? KiroPaths.globalMcpConfig() : KiroPaths.workspaceMcpConfig(scope.workspaceRoot());
    }

    private void reloadTable() {
        WorkspaceScope scope = currentScope();
        McpConfigFile config = scope == null
            ? new McpConfigFile()
            : (scope.isGlobal() ? configService.loadGlobal() : configService.loadWorkspace(scope.workspaceRoot()));
        tableModel.setConfig(config);
        updateButtonState();

        Path configPath = currentConfigPath();
        if (configPath != null) {
            watcher.watch(configPath.getParent());
        }
    }

    private void updateButtonState() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        removeButton.setEnabled(hasSelection);
        toggleEnabledButton.setEnabled(hasSelection);
    }

    private void persist() {
        WorkspaceScope scope = currentScope();
        if (scope == null) {
            return;
        }
        try {
            if (scope.isGlobal()) {
                configService.saveGlobal(tableModel.getConfig());
            } else {
                configService.saveWorkspace(scope.workspaceRoot(), tableModel.getConfig());
            }
        } catch (IOException ex) {
            logger.error("Failed to save MCP config", ex);
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAdd() {
        McpServerEditDialog dialog =
            new McpServerEditDialog((Frame) SwingUtilities.getWindowAncestor(this), tableModel.getConfig(), null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            persist();
            reloadTable();
        }
    }

    private void onBrowseCatalog() {
        McpCatalogDialog dialog = new McpCatalogDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), tableModel::getConfig,
            () -> { persist(); reloadTable(); });
        dialog.setVisible(true);
    }

    private void onEdit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        String name = tableModel.nameAt(row);
        McpServerEditDialog dialog =
            new McpServerEditDialog((Frame) SwingUtilities.getWindowAncestor(this), tableModel.getConfig(), name);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            persist();
            reloadTable();
        }
    }

    private void onToggleEnabled() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        McpServerConfig server = tableModel.configAt(row);
        server.setDisabled(!server.isDisabled());
        persist();
        reloadTable();
    }

    private void onRemove() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        String name = tableModel.nameAt(row);
        if (!SwingLayoutUtils.confirmDelete(this, "Remove Server",
                "Remove MCP server \"" + name + "\"? This cannot be undone.")) {
            return;
        }
        tableModel.getConfig().getMcpServers().remove(name);
        persist();
        reloadTable();
    }

    private void onReveal() {
        DesktopUtils.revealInFileManager(this, currentConfigPath());
    }

    private void onEditRawJson() {
        Path path = currentConfigPath();
        if (path == null) {
            return;
        }
        RawJsonEditorDialog dialog = new RawJsonEditorDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Edit Raw JSON: " + path,
            path,
            "{\n  \"mcpServers\": {}\n}\n");
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reloadTable();
        }
    }
}

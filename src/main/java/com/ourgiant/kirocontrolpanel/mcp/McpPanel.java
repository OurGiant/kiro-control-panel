package com.ourgiant.kirocontrolpanel.mcp;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.RawJsonEditorDialog;
import com.ourgiant.kirocontrolpanel.util.ScopePickerDialog;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import com.ourgiant.kirocontrolpanel.util.WorkspaceScopeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * MCP servers tab: lists mcpServers from the current scope's mcp.json and
 * lets the user add/edit/enable/disable/remove entries, or drop to a raw
 * JSON editor for anything the structured form doesn't cover.
 */
public class McpPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(McpPanel.class);

    private final McpConfigService configService = new McpConfigService();
    private final AppPreferences preferences;
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final McpServerTableModel tableModel = new McpServerTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<McpServerTableModel> rowSorter = new TableRowSorter<>(tableModel);
    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::applyFilter);
    private final JLabel registryWarningLabel = new JLabel(" ");

    private JButton editButton;
    private JButton duplicateButton;
    private JButton copyToButton;
    private JButton removeButton;
    private JButton toggleEnabledButton;
    private JButton revealButton;

    public McpPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.preferences = preferences;
        this.watcher = watcher;
        watcher.addListener(this::reloadTable);

        scopeBar = new WorkspaceScopeBar(preferences);
        scopeBar.addScopeChangeListener(scope -> reloadTable());

        registryWarningLabel.setForeground(new Color(0xB35900));
        registryWarningLabel.setVisible(false);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(scopeBar, BorderLayout.NORTH);
        northPanel.add(registryWarningLabel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        checkMcpRegistryStatus();

        table.setRowSorter(rowSorter);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) != -1) {
                    onEdit();
                }
            }
        });
        add(SwingLayoutUtils.createFilterableContent(filterField, new JScrollPane(table)), BorderLayout.CENTER);
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

        duplicateButton = new JButton("Duplicate...");
        duplicateButton.addActionListener(e -> onDuplicate());

        copyToButton = new JButton("Copy to...");
        copyToButton.addActionListener(e -> onCopyTo());

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
            addButton, browseCatalogButton, editButton, duplicateButton, copyToButton,
            toggleEnabledButton, removeButton, rawJsonButton, revealButton);
    }

    /** Every scope (Global + pinned workspaces) other than {@code current} -- the destination choices for "Copy to...". */
    private List<WorkspaceScope> otherScopes(WorkspaceScope current) {
        List<WorkspaceScope> scopes = new ArrayList<>();
        scopes.add(WorkspaceScope.global());
        scopes.addAll(WorkspaceScope.pinnedWorkspaces(preferences));
        scopes.remove(current);
        return scopes;
    }

    private WorkspaceScope currentScope() {
        return scopeBar.getSelectedScope();
    }

    /**
     * Best-effort, background, checked once per panel lifetime (app startup) -- see
     * {@link McpRegistryStatusService} for what this actually detects and why it's confined to
     * text-parsing kiro-cli's own CLI output. Never blocks the EDT and never shows anything for
     * an inconclusive check; only a confidently-detected registry failure produces a warning.
     */
    private void checkMcpRegistryStatus() {
        SwingWorker<McpRegistryStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected McpRegistryStatus doInBackground() {
                return McpRegistryStatusService.checkStatus();
            }

            @Override
            protected void done() {
                McpRegistryStatus status;
                try {
                    status = get();
                } catch (Exception e) {
                    logger.warn("MCP registry status check failed unexpectedly", e);
                    return;
                }
                if (status.registryUnreachable()) {
                    registryWarningLabel.setText(status.httpStatusCode() != null
                        ? "⚠ Your organization's MCP registry is unreachable (HTTP " + status.httpStatusCode()
                            + ") -- all MCP servers are currently unavailable, regardless of local configuration."
                        : "⚠ Your organization's MCP registry is unreachable -- all MCP servers are "
                            + "currently unavailable, regardless of local configuration.");
                    registryWarningLabel.setVisible(true);
                }
            }
        };
        worker.execute();
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
        duplicateButton.setEnabled(hasSelection);
        copyToButton.setEnabled(hasSelection);
        removeButton.setEnabled(hasSelection);
        toggleEnabledButton.setEnabled(hasSelection);
    }

    private void applyFilter() {
        String query = filterField.getText();
        rowSorter.setRowFilter(query == null || query.isBlank()
            ? null
            : RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
    }

    /**
     * The table's row sorter means {@link JTable#getSelectedRow()} returns a view index, which
     * only matches a model index (what {@link McpServerTableModel#nameAt}/{@code configAt} expect)
     * when nothing is filtered/sorted -- always convert before indexing into the model.
     */
    private int selectedModelRow() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
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
        int row = selectedModelRow();
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

    private void onDuplicate() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        String originalName = tableModel.nameAt(row);
        McpServerConfig original = tableModel.configAt(row);
        String suggestedName = SwingLayoutUtils.suggestCopyName(originalName,
            candidate -> tableModel.getConfig().getMcpServers().containsKey(candidate));
        McpServerEditDialog dialog = new McpServerEditDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), tableModel.getConfig(), suggestedName, original);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            persist();
            reloadTable();
        }
    }

    private void onCopyTo() {
        WorkspaceScope current = currentScope();
        int row = selectedModelRow();
        if (current == null || row < 0) {
            return;
        }
        List<WorkspaceScope> destinations = otherScopes(current);
        if (destinations.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Pin another workspace first to copy to it.", "No Other Scopes", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = tableModel.nameAt(row);
        McpServerConfig original = tableModel.configAt(row);

        ScopePickerDialog picker = new ScopePickerDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), name, destinations);
        picker.setVisible(true);
        if (!picker.isConfirmed()) {
            return;
        }
        WorkspaceScope target = picker.getSelectedScope();

        McpConfigFile targetConfig = target.isGlobal() ? configService.loadGlobal() : configService.loadWorkspace(target.workspaceRoot());
        String targetName = SwingLayoutUtils.suggestNameAvoidingCollision(name, targetConfig.getMcpServers()::containsKey);

        McpServerEditDialog dialog = new McpServerEditDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), targetConfig, targetName, original);
        dialog.setVisible(true);
        if (!dialog.isSaved()) {
            return;
        }
        try {
            if (target.isGlobal()) {
                configService.saveGlobal(targetConfig);
            } else {
                configService.saveWorkspace(target.workspaceRoot(), targetConfig);
            }
        } catch (IOException ex) {
            logger.error("Failed to copy MCP server \"{}\" to {}", name, target, ex);
            JOptionPane.showMessageDialog(this, "Failed to copy: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onToggleEnabled() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        McpServerConfig server = tableModel.configAt(row);
        server.setDisabled(!server.isDisabled());
        persist();
        reloadTable();
    }

    private void onRemove() {
        int row = selectedModelRow();
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

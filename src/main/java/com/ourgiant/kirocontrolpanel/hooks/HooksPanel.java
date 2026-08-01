package com.ourgiant.kirocontrolpanel.hooks;

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
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hooks tab: lists .kiro/hooks/*.json entries for a pinned workspace (hooks
 * are workspace-only in Kiro — no Global scope) and lets the user
 * add/edit/enable/disable/remove them, or drop to a raw JSON editor.
 */
public class HooksPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(HooksPanel.class);
    private static final Pattern VALID_FILE_SLUG = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final String DEFAULT_TRIGGER = "file_save";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final HookService hookService = new HookService();
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final HookTableModel tableModel = new HookTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<HookTableModel> rowSorter = new TableRowSorter<>(tableModel);
    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::applyFilter);

    private JButton editButton;
    private JButton duplicateButton;
    private JButton removeButton;
    private JButton toggleEnabledButton;
    private JButton rawJsonButton;
    private JButton revealButton;

    public HooksPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.watcher = watcher;
        watcher.addListener(this::reloadTable);

        scopeBar = new WorkspaceScopeBar(preferences, false);
        scopeBar.addScopeChangeListener(scope -> reloadTable());
        add(scopeBar, BorderLayout.NORTH);

        table.setRowSorter(rowSorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        duplicateButton = new JButton("Duplicate...");
        duplicateButton.addActionListener(e -> onDuplicate());

        toggleEnabledButton = new JButton("Enable/Disable");
        toggleEnabledButton.setMnemonic(KeyEvent.VK_T);
        toggleEnabledButton.addActionListener(e -> onToggleEnabled());

        removeButton = new JButton("Remove");
        removeButton.setMnemonic(KeyEvent.VK_R);
        removeButton.addActionListener(e -> onRemove());

        rawJsonButton = new JButton("Edit Raw JSON...");
        rawJsonButton.addActionListener(e -> onEditRawJson());

        revealButton = new JButton("Reveal File...");
        revealButton.addActionListener(e -> onReveal());

        return SwingLayoutUtils.createVerticalButtonPanel(
            addButton, editButton, duplicateButton, toggleEnabledButton, removeButton, rawJsonButton, revealButton);
    }

    private void reloadTable() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        List<HookEntry> entries = scope == null ? List.of() : hookService.listWorkspace(scope.workspaceRoot());
        tableModel.setEntries(entries);
        updateButtonState();

        if (scope != null) {
            watcher.watch(KiroPaths.workspaceHooksDir(scope.workspaceRoot()));
        }
    }

    private void updateButtonState() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        duplicateButton.setEnabled(hasSelection);
        removeButton.setEnabled(hasSelection);
        toggleEnabledButton.setEnabled(hasSelection);
        rawJsonButton.setEnabled(hasSelection);
        revealButton.setEnabled(hasSelection);
    }

    private void applyFilter() {
        String query = filterField.getText();
        rowSorter.setRowFilter(query == null || query.isBlank()
            ? null
            : RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
    }

    /**
     * The table's row sorter means {@link JTable#getSelectedRow()} returns a view index, which
     * only matches a model index (what {@link HookTableModel#entryAt} expects) when nothing is
     * filtered/sorted -- always convert before indexing into the model.
     */
    private int selectedModelRow() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? -1 : table.convertRowIndexToModel(viewRow);
    }

    private void onAdd() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope == null) {
            JOptionPane.showMessageDialog(this,
                "Add a workspace first — hooks are workspace-only in Kiro.",
                "No Workspace Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String slug = JOptionPane.showInputDialog(this,
            "Hook file name (lowercase, hyphenated, e.g. lint-on-save):", "New Hook", JOptionPane.PLAIN_MESSAGE);
        if (slug == null || slug.isBlank()) {
            return;
        }
        slug = slug.trim();
        if (!VALID_FILE_SLUG.matcher(slug).matches()) {
            JOptionPane.showMessageDialog(this,
                "File names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Hook hook = new Hook();
        hook.setName(slug);
        hook.setTrigger(DEFAULT_TRIGGER);
        hook.setEnabled(true);
        hook.setTimeout(DEFAULT_TIMEOUT_SECONDS);
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_COMMAND);
        hook.setAction(action);

        HookEditDialog dialog = new HookEditDialog((Frame) SwingUtilities.getWindowAncestor(this), hook, true);
        dialog.setVisible(true);
        if (!dialog.isSaved()) {
            return;
        }
        try {
            hookService.createHookFile(scope.workspaceRoot(), slug, hook);
            reloadTable();
        } catch (IOException ex) {
            logger.error("Failed to create hook file", ex);
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onEdit() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        HookEntry entry = tableModel.entryAt(row);
        HookEditDialog dialog =
            new HookEditDialog((Frame) SwingUtilities.getWindowAncestor(this), entry.getHook(), false);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            persist(entry);
        }
    }

    private void onDuplicate() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        int row = table.getSelectedRow();
        if (scope == null || row < 0) {
            return;
        }
        HookEntry entry = tableModel.entryAt(row);
        String suggestedSlug = SwingLayoutUtils.suggestCopyName(entry.getHook().getName(),
            candidate -> Files.exists(KiroPaths.workspaceHooksDir(scope.workspaceRoot()).resolve(candidate + ".json")));

        String slug = (String) JOptionPane.showInputDialog(this,
            "Hook file name (lowercase, hyphenated, e.g. lint-on-save):", "Duplicate Hook",
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedSlug);
        if (slug == null || slug.isBlank()) {
            return;
        }
        slug = slug.trim();
        if (!VALID_FILE_SLUG.matcher(slug).matches()) {
            JOptionPane.showMessageDialog(this,
                "File names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Hook copy = hookService.copy(entry.getHook());
        copy.setName(slug);

        HookEditDialog dialog = new HookEditDialog((Frame) SwingUtilities.getWindowAncestor(this), copy, true);
        dialog.setVisible(true);
        if (!dialog.isSaved()) {
            return;
        }
        try {
            hookService.createHookFile(scope.workspaceRoot(), slug, copy);
            reloadTable();
        } catch (IOException ex) {
            logger.error("Failed to create hook file", ex);
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onToggleEnabled() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        HookEntry entry = tableModel.entryAt(row);
        entry.getHook().setEnabled(!entry.getHook().isEnabled());
        persist(entry);
    }

    private void onRemove() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        HookEntry entry = tableModel.entryAt(row);
        if (!SwingLayoutUtils.confirmDelete(this, "Remove Hook",
                "Remove hook \"" + entry.getHook().getName() + "\"? This cannot be undone.")) {
            return;
        }
        try {
            hookService.delete(entry);
            reloadTable();
        } catch (IOException ex) {
            logger.error("Failed to remove hook", ex);
            JOptionPane.showMessageDialog(this, "Failed to remove: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onReveal() {
        int row = selectedModelRow();
        if (row >= 0) {
            DesktopUtils.revealInFileManager(this, tableModel.entryAt(row).getFilePath());
        }
    }

    private void onEditRawJson() {
        int row = selectedModelRow();
        if (row < 0) {
            return;
        }
        Path filePath = tableModel.entryAt(row).getFilePath();
        RawJsonEditorDialog dialog = new RawJsonEditorDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Edit Raw JSON: " + filePath,
            filePath,
            "{\n  \"version\": \"v1\",\n  \"hooks\": []\n}\n");
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reloadTable();
        }
    }

    private void persist(HookEntry entry) {
        try {
            hookService.save(entry.getFilePath(), entry.getFile());
            reloadTable();
        } catch (IOException ex) {
            logger.error("Failed to save hook", ex);
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

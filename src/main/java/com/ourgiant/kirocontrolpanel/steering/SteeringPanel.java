package com.ourgiant.kirocontrolpanel.steering;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import com.ourgiant.kirocontrolpanel.util.TextFilter;
import com.ourgiant.kirocontrolpanel.util.WorkspaceScopeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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
 * Steering docs tab: lists .kiro/steering/*.md for a Global or pinned-workspace
 * scope and lets the user create/edit/delete them.
 */
public class SteeringPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(SteeringPanel.class);
    private static final Pattern VALID_STEERING_FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final SteeringService steeringService = new SteeringService();
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final DefaultListModel<SteeringDoc> docListModel = new DefaultListModel<>();
    private final JList<SteeringDoc> docList = new JList<>(docListModel);
    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::applyFilter);
    private List<SteeringDoc> allDocs = List.of();

    private JButton editButton;
    private JButton deleteButton;
    private JButton revealButton;

    public SteeringPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.watcher = watcher;
        watcher.addListener(this::reloadDocList);

        scopeBar = new WorkspaceScopeBar(preferences);
        scopeBar.addScopeChangeListener(scope -> reloadDocList());
        add(scopeBar, BorderLayout.NORTH);

        docList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SteeringDoc doc) {
                    setText(doc.getFileName());
                }
                return c;
            }
        });
        docList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        docList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && docList.getSelectedValue() != null) {
                    onEdit();
                }
            }
        });
        add(SwingLayoutUtils.createFilterableContent(filterField, new JScrollPane(docList)), BorderLayout.CENTER);
        add(createSideButtons(), BorderLayout.EAST);

        reloadDocList();
    }

    private JPanel createSideButtons() {
        JButton newButton = new JButton("New...");
        newButton.setMnemonic(KeyEvent.VK_N);
        newButton.addActionListener(e -> onNew());

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        deleteButton = new JButton("Delete");
        deleteButton.setMnemonic(KeyEvent.VK_D);
        deleteButton.addActionListener(e -> onDelete());

        revealButton = new JButton("Reveal File...");
        revealButton.addActionListener(e -> onReveal());

        return SwingLayoutUtils.createVerticalButtonPanel(newButton, editButton, deleteButton, revealButton);
    }

    private void reloadDocList() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope != null) {
            allDocs = scope.isGlobal()
                ? steeringService.listGlobal()
                : steeringService.listWorkspace(scope.workspaceRoot());
            watcher.watch(scope.isGlobal()
                ? KiroPaths.globalSteeringDir()
                : KiroPaths.workspaceSteeringDir(scope.workspaceRoot()));
        } else {
            allDocs = List.of();
        }
        applyFilter();
    }

    private void applyFilter() {
        docListModel.clear();
        String query = filterField.getText();
        for (SteeringDoc doc : allDocs) {
            if (TextFilter.matches(doc.getFileName(), query)) {
                docListModel.addElement(doc);
            }
        }
        updateButtonState();
    }

    private void updateButtonState() {
        boolean hasSelection = docList.getSelectedValue() != null;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        revealButton.setEnabled(hasSelection);
    }

    private void onNew() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope == null) {
            return;
        }
        String fileName = JOptionPane.showInputDialog(this,
            "File name (e.g. api-conventions.md):", "New Steering Doc", JOptionPane.PLAIN_MESSAGE);
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        fileName = fileName.trim();
        String baseName = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;
        if (!VALID_STEERING_FILE_NAME.matcher(baseName).matches()) {
            JOptionPane.showMessageDialog(this,
                "File names should be lowercase letters, digits, and hyphens only (optionally ending in .md).",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        fileName = baseName + ".md";

        Path dir = scope.isGlobal()
            ? KiroPaths.globalSteeringDir()
            : KiroPaths.workspaceSteeringDir(scope.workspaceRoot());
        Path filePath = dir.resolve(fileName);
        if (Files.exists(filePath)) {
            JOptionPane.showMessageDialog(this,
                "A file named \"" + fileName + "\" already exists.", "Duplicate File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SteeringDoc doc = new SteeringDoc(filePath, scope.workspaceRoot());
        doc.setBody(SteeringTemplates.bodyFor(fileName));
        SteeringEditorDialog dialog =
            new SteeringEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), steeringService, doc);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reloadDocList();
        }
    }

    private void onEdit() {
        SteeringDoc selected = docList.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            SteeringDoc fresh = steeringService.load(selected.getFilePath(), selected.getWorkspaceRoot());
            SteeringEditorDialog dialog =
                new SteeringEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), steeringService, fresh);
            dialog.setVisible(true);
            if (dialog.isSaved()) {
                reloadDocList();
            }
        } catch (IOException ex) {
            logger.error("Failed to load steering doc: {}", selected.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to open: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDelete() {
        SteeringDoc selected = docList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!SwingLayoutUtils.confirmDelete(this, "Delete Steering Doc",
                "Delete \"" + selected.getFileName() + "\"? This cannot be undone.")) {
            return;
        }
        try {
            steeringService.delete(selected);
            reloadDocList();
        } catch (IOException ex) {
            logger.error("Failed to delete steering doc: {}", selected.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to delete: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onReveal() {
        SteeringDoc selected = docList.getSelectedValue();
        if (selected != null) {
            DesktopUtils.revealInFileManager(this, selected.getFilePath());
        }
    }
}

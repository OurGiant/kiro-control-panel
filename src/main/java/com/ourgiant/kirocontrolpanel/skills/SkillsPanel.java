package com.ourgiant.kirocontrolpanel.skills;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.ScopePickerDialog;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Skills tab: lists .kiro/skills/&lt;name&gt;/SKILL.md folders for a Global
 * or pinned-workspace scope and lets the user create/edit/delete them.
 */
public class SkillsPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(SkillsPanel.class);
    private static final Pattern VALID_SKILL_FOLDER_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final SkillService skillService = new SkillService();
    private final AppPreferences preferences;
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final DefaultListModel<Skill> skillListModel = new DefaultListModel<>();
    private final JList<Skill> skillList = new JList<>(skillListModel);
    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::applyFilter);
    private List<Skill> allSkills = List.of();

    private JButton editButton;
    private JButton duplicateButton;
    private JButton copyToButton;
    private JButton deleteButton;
    private JButton revealButton;

    public SkillsPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.preferences = preferences;
        this.watcher = watcher;
        watcher.addListener(this::reloadSkillList);

        scopeBar = new WorkspaceScopeBar(preferences);
        scopeBar.addScopeChangeListener(scope -> reloadSkillList());
        add(scopeBar, BorderLayout.NORTH);

        skillList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Skill skill) {
                    setText(skill.getSkillFolderName());
                }
                return c;
            }
        });
        skillList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        skillList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && skillList.getSelectedValue() != null) {
                    onEdit();
                }
            }
        });
        add(SwingLayoutUtils.createFilterableContent(filterField, new JScrollPane(skillList)), BorderLayout.CENTER);
        add(createSideButtons(), BorderLayout.EAST);

        reloadSkillList();
    }

    private JPanel createSideButtons() {
        JButton newButton = new JButton("New...");
        newButton.setMnemonic(KeyEvent.VK_N);
        newButton.addActionListener(e -> onNew());

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        duplicateButton = new JButton("Duplicate...");
        duplicateButton.addActionListener(e -> onDuplicate());

        copyToButton = new JButton("Copy to...");
        copyToButton.addActionListener(e -> onCopyTo());

        deleteButton = new JButton("Delete");
        deleteButton.setMnemonic(KeyEvent.VK_D);
        deleteButton.addActionListener(e -> onDelete());

        revealButton = new JButton("Reveal Folder...");
        revealButton.addActionListener(e -> onReveal());

        return SwingLayoutUtils.createVerticalButtonPanel(
            newButton, editButton, duplicateButton, copyToButton, deleteButton, revealButton);
    }

    /** Every scope (Global + pinned workspaces) other than {@code current} -- the destination choices for "Copy to...". */
    private List<WorkspaceScope> otherScopes(WorkspaceScope current) {
        List<WorkspaceScope> scopes = new ArrayList<>();
        scopes.add(WorkspaceScope.global());
        scopes.addAll(WorkspaceScope.pinnedWorkspaces(preferences));
        scopes.remove(current);
        return scopes;
    }

    private void reloadSkillList() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope != null) {
            allSkills = scope.isGlobal()
                ? skillService.listGlobal()
                : skillService.listWorkspace(scope.workspaceRoot());
            for (Skill skill : allSkills) {
                // Watching only the parent skills/ dir catches add/remove of a whole
                // skill folder, not edits to an existing skill's own files, so each
                // skill's folder needs its own registration too.
                watcher.watch(skill.getSkillDir());
            }
            watcher.watch(scope.isGlobal()
                ? KiroPaths.globalSkillsDir()
                : KiroPaths.workspaceSkillsDir(scope.workspaceRoot()));
        } else {
            allSkills = List.of();
        }
        applyFilter();
    }

    private void applyFilter() {
        skillListModel.clear();
        String query = filterField.getText();
        for (Skill skill : allSkills) {
            if (TextFilter.matches(skill.getSkillFolderName(), query)) {
                skillListModel.addElement(skill);
            }
        }
        updateButtonState();
    }

    private void updateButtonState() {
        boolean hasSelection = skillList.getSelectedValue() != null;
        editButton.setEnabled(hasSelection);
        duplicateButton.setEnabled(hasSelection);
        copyToButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        revealButton.setEnabled(hasSelection);
    }

    private void onNew() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope == null) {
            return;
        }
        String folderName = JOptionPane.showInputDialog(this,
            "Skill folder name (lowercase, hyphenated, e.g. pdf-processing):", "New Skill", JOptionPane.PLAIN_MESSAGE);
        if (folderName == null || folderName.isBlank()) {
            return;
        }
        folderName = folderName.trim();
        if (!VALID_SKILL_FOLDER_NAME.matcher(folderName).matches()) {
            JOptionPane.showMessageDialog(this,
                "Skill folder names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path skillsDir = scope.isGlobal()
            ? KiroPaths.globalSkillsDir()
            : KiroPaths.workspaceSkillsDir(scope.workspaceRoot());
        Path skillDir = skillsDir.resolve(folderName);
        if (Files.exists(skillDir)) {
            JOptionPane.showMessageDialog(this,
                "A skill named \"" + folderName + "\" already exists.", "Duplicate Skill", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Skill skill = new Skill(skillDir, scope.workspaceRoot());
        skill.setName(folderName);
        skill.setBody("## Instructions\n\nDescribe how Kiro should use this skill.\n");
        SkillEditorDialog dialog =
            new SkillEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), skillService, skill);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            reloadSkillList();
        }
    }

    private void onEdit() {
        Skill selected = skillList.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            Skill fresh = skillService.load(selected.getSkillDir(), selected.getWorkspaceRoot());
            SkillEditorDialog dialog =
                new SkillEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), skillService, fresh);
            dialog.setVisible(true);
            if (dialog.isSaved()) {
                reloadSkillList();
            }
        } catch (IOException ex) {
            logger.error("Failed to load skill: {}", selected.getSkillDir(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to open: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDuplicate() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        Skill selected = skillList.getSelectedValue();
        if (scope == null || selected == null) {
            return;
        }
        Path skillsDir = scope.isGlobal()
            ? KiroPaths.globalSkillsDir()
            : KiroPaths.workspaceSkillsDir(scope.workspaceRoot());
        String suggestedName = SwingLayoutUtils.suggestCopyName(selected.getSkillFolderName(),
            candidate -> Files.exists(skillsDir.resolve(candidate)));

        String folderName = (String) JOptionPane.showInputDialog(this,
            "Skill folder name (lowercase, hyphenated, e.g. pdf-processing):", "Duplicate Skill",
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedName);
        if (folderName == null || folderName.isBlank()) {
            return;
        }
        folderName = folderName.trim();
        if (!VALID_SKILL_FOLDER_NAME.matcher(folderName).matches()) {
            JOptionPane.showMessageDialog(this,
                "Skill folder names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path skillDir = skillsDir.resolve(folderName);
        if (Files.exists(skillDir)) {
            JOptionPane.showMessageDialog(this,
                "A skill named \"" + folderName + "\" already exists.", "Duplicate Skill", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Skill fresh = skillService.load(selected.getSkillDir(), selected.getWorkspaceRoot());
            Skill copy = new Skill(skillDir, scope.workspaceRoot());
            copy.setName(fresh.getName());
            copy.setDescription(fresh.getDescription());
            copy.setBody(fresh.getBody());
            copy.getExtraFrontMatter().putAll(fresh.getExtraFrontMatter());
            // Deliberately SKILL.md-only (name/description/body/front matter), not a full folder
            // copy -- bundled scripts/references/assets are read-only in this app's own editor
            // (never created by "New..." either), so Duplicate stays consistent with what the
            // in-app editor actually manages rather than silently doing more than New does.

            SkillEditorDialog dialog =
                new SkillEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), skillService, copy);
            dialog.setVisible(true);
            if (dialog.isSaved()) {
                reloadSkillList();
            }
        } catch (IOException ex) {
            logger.error("Failed to duplicate skill: {}", selected.getSkillDir(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to duplicate: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCopyTo() {
        WorkspaceScope current = scopeBar.getSelectedScope();
        Skill selected = skillList.getSelectedValue();
        if (current == null || selected == null) {
            return;
        }
        List<WorkspaceScope> destinations = otherScopes(current);
        if (destinations.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Pin another workspace first to copy to it.", "No Other Scopes", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ScopePickerDialog picker = new ScopePickerDialog(
            (Frame) SwingUtilities.getWindowAncestor(this), selected.getSkillFolderName(), destinations);
        picker.setVisible(true);
        if (!picker.isConfirmed()) {
            return;
        }
        WorkspaceScope target = picker.getSelectedScope();

        Path targetSkillsDir = target.isGlobal() ? KiroPaths.globalSkillsDir() : KiroPaths.workspaceSkillsDir(target.workspaceRoot());
        String targetFolderName = SwingLayoutUtils.suggestNameAvoidingCollision(selected.getSkillFolderName(),
            candidate -> Files.exists(targetSkillsDir.resolve(candidate)));
        Path targetSkillDir = targetSkillsDir.resolve(targetFolderName);

        try {
            Skill fresh = skillService.load(selected.getSkillDir(), selected.getWorkspaceRoot());
            Skill copy = new Skill(targetSkillDir, target.workspaceRoot());
            copy.setName(fresh.getName());
            copy.setDescription(fresh.getDescription());
            copy.setBody(fresh.getBody());
            copy.getExtraFrontMatter().putAll(fresh.getExtraFrontMatter());
            // SKILL.md-only, same scope as "Duplicate" -- see its comment for why bundled
            // scripts/references/assets aren't copied.

            SkillEditorDialog dialog =
                new SkillEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), skillService, copy);
            dialog.setVisible(true);
        } catch (IOException ex) {
            logger.error("Failed to copy skill {} to {}", selected.getSkillDir(), target, ex);
            JOptionPane.showMessageDialog(this,
                "Failed to copy: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDelete() {
        Skill selected = skillList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!SwingLayoutUtils.confirmDelete(this, "Delete Skill",
                "Delete skill \"" + selected.getSkillFolderName() + "\" and everything in its folder? This cannot be undone.")) {
            return;
        }
        try {
            skillService.delete(selected);
            reloadSkillList();
        } catch (IOException ex) {
            logger.error("Failed to delete skill: {}", selected.getSkillDir(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to delete: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onReveal() {
        Skill selected = skillList.getSelectedValue();
        if (selected != null) {
            DesktopUtils.revealInFileManager(this, selected.getSkillDir());
        }
    }
}

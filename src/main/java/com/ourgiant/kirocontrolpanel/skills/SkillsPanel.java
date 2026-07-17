package com.ourgiant.kirocontrolpanel.skills;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.WorkspaceScopeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final WorkspaceScopeBar scopeBar;

    private final DefaultListModel<Skill> skillListModel = new DefaultListModel<>();
    private final JList<Skill> skillList = new JList<>(skillListModel);

    private JButton editButton;
    private JButton deleteButton;

    public SkillsPanel(AppPreferences preferences) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

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
        add(new JScrollPane(skillList), BorderLayout.CENTER);
        add(createSideButtons(), BorderLayout.EAST);

        reloadSkillList();
    }

    private JPanel createSideButtons() {
        JPanel sideButtonPanel = new JPanel();
        sideButtonPanel.setLayout(new BoxLayout(sideButtonPanel, BoxLayout.Y_AXIS));

        JButton newButton = new JButton("New...");
        newButton.setMnemonic(KeyEvent.VK_N);
        newButton.addActionListener(e -> onNew());

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        deleteButton = new JButton("Delete");
        deleteButton.setMnemonic(KeyEvent.VK_D);
        deleteButton.addActionListener(e -> onDelete());

        for (JButton button : new JButton[] {newButton, editButton, deleteButton}) {
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            sideButtonPanel.add(button);
            sideButtonPanel.add(Box.createVerticalStrut(6));
        }
        sideButtonPanel.add(Box.createVerticalGlue());
        return sideButtonPanel;
    }

    private void reloadSkillList() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        skillListModel.clear();
        if (scope != null) {
            List<Skill> skills = scope.isGlobal()
                ? skillService.listGlobal()
                : skillService.listWorkspace(scope.workspaceRoot());
            for (Skill skill : skills) {
                skillListModel.addElement(skill);
            }
        }
        updateButtonState();
    }

    private void updateButtonState() {
        boolean hasSelection = skillList.getSelectedValue() != null;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
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

    private void onDelete() {
        Skill selected = skillList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete skill \"" + selected.getSkillFolderName() + "\" and everything in its folder? This cannot be undone.",
            "Delete Skill",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
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
}

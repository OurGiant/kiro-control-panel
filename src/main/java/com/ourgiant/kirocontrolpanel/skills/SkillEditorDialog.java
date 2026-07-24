package com.ourgiant.kirocontrolpanel.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Dialog for editing a skill: SKILL.md front matter + body on one tab, a
 * read-only browser for its scripts/references/assets folders on another.
 */
public class SkillEditorDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(SkillEditorDialog.class);
    private static final String SKILL_MD = "SKILL.md";

    private final SkillService skillService;
    private final Skill skill;

    private JTextField nameField;
    private JTextField descriptionField;
    private JTextArea bodyArea;

    private boolean saved = false;

    public SkillEditorDialog(Frame parent, SkillService skillService, Skill skill) {
        super(parent, skill.getSkillFolderName(), true);
        this.skillService = skillService;
        this.skill = skill;

        initializeUI();
        loadFromSkill();
        // Content-driven pack() alone can blow up past the screen: an unbounded JTextField
        // sizes to its current text (no columns set), and bodyArea's wrapped JTextArea has
        // no rows/columns floor either, so a long description or SKILL.md body drives pack()
        // to an oversized preferred size instead of the normal "wrap within the window" behavior.
        // An explicit preferred size overrides that; still resizable larger from here. See #48.
        setPreferredSize(new Dimension(1024, 768));
        pack();
        setMinimumSize(new Dimension(600, 520));
        setLocationRelativeTo(parent);
    }

    public boolean isSaved() {
        return saved;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("SKILL.md", createSkillMdTab());
        tabs.addTab("Files", createFilesTab());
        add(tabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setMnemonic(KeyEvent.VK_C);
        JButton saveButton = new JButton("Save");
        saveButton.setMnemonic(KeyEvent.VK_S);
        cancelButton.addActionListener(e -> setVisible(false));
        saveButton.addActionListener(e -> onSave());
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createSkillMdTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel frontMatterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        frontMatterPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        nameField = new JTextField();
        nameField.setToolTipText("Short identifier Kiro shows/matches against; independent of the folder name");
        frontMatterPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        frontMatterPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        descriptionField = new JTextField();
        descriptionField.setToolTipText("Describes when Kiro should activate this skill — required");
        frontMatterPanel.add(descriptionField, gbc);

        panel.add(frontMatterPanel, BorderLayout.NORTH);

        bodyArea = new JTextArea();
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        panel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);

        return panel;
    }

    private JScrollPane createFilesTab() {
        DefaultMutableTreeNode root = buildAssetTree(skill.getSkillDir());
        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setEditable(false);
        return new JScrollPane(tree);
    }

    private DefaultMutableTreeNode buildAssetTree(Path dir) {
        String label = dir.getFileName() != null ? dir.getFileName().toString() : dir.toString();
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(label);
        if (!Files.isDirectory(dir)) {
            return node;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(p -> !p.getFileName().toString().equals(SKILL_MD))
                .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p)).thenComparing(Path::getFileName))
                .forEach(p -> node.add(Files.isDirectory(p)
                    ? buildAssetTree(p)
                    : new DefaultMutableTreeNode(p.getFileName().toString())));
        } catch (IOException e) {
            logger.warn("Failed to list skill assets under {}", dir, e);
        }
        return node;
    }

    private void loadFromSkill() {
        nameField.setText(skill.getName());
        descriptionField.setText(skill.getDescription());
        bodyArea.setText(skill.getBody());
        bodyArea.setCaretPosition(0);
    }

    private void onSave() {
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();
        if (name.isEmpty() || description.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Name and description are both required — Kiro matches skills against them.",
                "Missing Fields",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        skill.setName(name);
        skill.setDescription(description);
        skill.setBody(bodyArea.getText());

        try {
            skillService.save(skill);
            saved = true;
            setVisible(false);
        } catch (IOException ex) {
            logger.error("Failed to save skill: {}", skill.getSkillDir(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

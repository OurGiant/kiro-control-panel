package com.ourgiant.kirocontrolpanel.steering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for editing a single steering doc: front-matter fields plus Markdown body.
 */
public class SteeringEditorDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(SteeringEditorDialog.class);
    private static final String[] INCLUSION_MODES = {"always", "fileMatch", "manual", "auto"};

    private final SteeringService steeringService;
    private final SteeringDoc doc;

    private JComboBox<String> inclusionCombo;
    private JTextField fileMatchPatternField;
    private JTextField nameField;
    private JTextField descriptionField;
    private JTextArea bodyArea;

    private boolean saved = false;

    public SteeringEditorDialog(Frame parent, SteeringService steeringService, SteeringDoc doc) {
        super(parent, doc.getFileName(), true);
        this.steeringService = steeringService;
        this.doc = doc;

        initializeUI();
        loadFromDoc();
        pack();
        setMinimumSize(new Dimension(560, 480));
        setLocationRelativeTo(parent);
    }

    public boolean isSaved() {
        return saved;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel frontMatterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        frontMatterPanel.add(new JLabel("Inclusion:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inclusionCombo = new JComboBox<>(INCLUSION_MODES);
        inclusionCombo.setToolTipText("always: loaded every interaction. fileMatch: only for matching files. "
            + "manual: only via #filename. auto: Kiro decides from name/description.");
        inclusionCombo.addActionListener(e -> updateFieldAvailability());
        frontMatterPanel.add(inclusionCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        frontMatterPanel.add(new JLabel("File Match Pattern:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fileMatchPatternField = new JTextField();
        fileMatchPatternField.setToolTipText("Comma-separated glob pattern(s), e.g. components/**/*.tsx, **/*.tsx");
        frontMatterPanel.add(fileMatchPatternField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        frontMatterPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        nameField = new JTextField();
        nameField.setToolTipText("Required for auto inclusion — short identifier Kiro matches requests against");
        frontMatterPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        frontMatterPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        descriptionField = new JTextField();
        descriptionField.setToolTipText("Required for auto inclusion — describes when Kiro should pull this doc in");
        frontMatterPanel.add(descriptionField, gbc);

        add(frontMatterPanel, BorderLayout.NORTH);

        bodyArea = new JTextArea();
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        add(new JScrollPane(bodyArea), BorderLayout.CENTER);

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

    private void updateFieldAvailability() {
        String mode = (String) inclusionCombo.getSelectedItem();
        fileMatchPatternField.setEnabled("fileMatch".equals(mode));
        boolean autoMode = "auto".equals(mode);
        nameField.setEnabled(autoMode);
        descriptionField.setEnabled(autoMode);
    }

    private void loadFromDoc() {
        inclusionCombo.setSelectedItem(doc.getInclusion());
        fileMatchPatternField.setText(String.join(", ", doc.getFileMatchPatterns()));
        nameField.setText(doc.getName());
        descriptionField.setText(doc.getDescription());
        bodyArea.setText(doc.getBody());
        bodyArea.setCaretPosition(0);
        updateFieldAvailability();
    }

    private void onSave() {
        String mode = (String) inclusionCombo.getSelectedItem();

        if ("fileMatch".equals(mode) && fileMatchPatternField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "File match pattern is required when inclusion is \"fileMatch\".",
                "Missing Pattern",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        if ("auto".equals(mode)
            && (nameField.getText().trim().isEmpty() || descriptionField.getText().trim().isEmpty())) {
            JOptionPane.showMessageDialog(this,
                "Name and description are required when inclusion is \"auto\".",
                "Missing Fields",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        doc.setInclusion(mode);
        doc.setFileMatchPatterns(parsePatterns(fileMatchPatternField.getText()));
        doc.setName(nameField.getText().trim());
        doc.setDescription(descriptionField.getText().trim());
        doc.setBody(bodyArea.getText());

        try {
            steeringService.save(doc);
            saved = true;
            setVisible(false);
        } catch (IOException ex) {
            logger.error("Failed to save steering doc: {}", doc.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to save: " + ex.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private static List<String> parsePatterns(String text) {
        List<String> patterns = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }
        return patterns;
    }
}

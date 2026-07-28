package com.ourgiant.kirocontrolpanel.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import com.ourgiant.kirocontrolpanel.changelog.ChangeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generic "edit this file as text" fallback dialog, for anything a
 * structured form doesn't cover (used by the MCP and Hooks panels' raw-JSON
 * fallback, and by {@link InAppFileEditorLauncher} for any surface whose
 * structured editor can't be used -- either a genuine file/multi-item
 * granularity mismatch, or content that fails to parse structurally).
 * Validates the content as JSON before writing it to disk by default;
 * pass {@code validateAsJson=false} for non-JSON content (e.g. a Steering
 * doc/SKILL.md's Markdown + YAML front matter).
 */
public class RawJsonEditorDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(RawJsonEditorDialog.class);
    private static final ObjectMapper VALIDATOR = new ObjectMapper();

    private final Path filePath;
    private final String defaultContentIfMissing;
    private final boolean validateAsJson;
    private JTextArea textArea;
    private boolean saved = false;

    public RawJsonEditorDialog(Frame parent, String title, Path filePath, String defaultContentIfMissing) {
        this(parent, title, filePath, defaultContentIfMissing, true);
    }

    public RawJsonEditorDialog(Frame parent, String title, Path filePath, String defaultContentIfMissing,
                                boolean validateAsJson) {
        super(parent, title, true);
        this.filePath = filePath;
        this.defaultContentIfMissing = defaultContentIfMissing;
        this.validateAsJson = validateAsJson;

        initializeUI();
        loadContent();
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(parent);
    }

    public boolean isSaved() {
        return saved;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        add(new JScrollPane(textArea), BorderLayout.CENTER);

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

    private void loadContent() {
        try {
            String content = Files.exists(filePath) ? Files.readString(filePath) : defaultContentIfMissing;
            textArea.setText(content);
            textArea.setCaretPosition(0);
        } catch (IOException ex) {
            logger.error("Failed to read {}", filePath, ex);
            JOptionPane.showMessageDialog(this, "Failed to read: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSave() {
        String content = textArea.getText();
        if (validateAsJson) {
            try {
                VALIDATOR.readTree(content);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "That doesn't parse as valid JSON:\n\n" + ex.getMessage(), "Invalid JSON", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        try {
            boolean existedBefore = Files.exists(filePath);
            SelfWriteTracker.markAboutToWrite(filePath.getParent());
            Files.createDirectories(filePath.getParent());
            SelfWriteTracker.markAboutToWrite(filePath);
            Files.writeString(filePath, content);
            logger.info("Saved raw JSON {}", filePath);
            GitAutoCommitter.commitIfEnabled(filePath);
            ChangeLogService.record(filePath, existedBefore ? ChangeKind.MODIFIED : ChangeKind.CREATED, ChangeSource.SELF);
            saved = true;
            setVisible(false);
        } catch (IOException ex) {
            logger.error("Failed to save {}", filePath, ex);
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

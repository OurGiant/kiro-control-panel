package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.KiroSessionLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * App-wide preferences -- theme, git tracking, and (Windows-only) PowerShell
 * profile skip -- previously scattered across a standalone "Config" menu.
 * None of this is per-tab/per-panel configuration, so it lives in its own
 * window off File > Settings... instead. Every control applies immediately,
 * same as the menu items it replaces -- there's no separate Save/Cancel state.
 */
public class SettingsDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);

    public SettingsDialog(Frame parent, AppPreferences preferences) {
        super(parent, "Settings", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        content.add(buildThemeSection(preferences));
        content.add(Box.createVerticalStrut(12));
        content.add(buildGitSection(preferences));

        if (KiroSessionLauncher.detect(System.getProperty("os.name")) == KiroSessionLauncher.Platform.WINDOWS) {
            content.add(Box.createVerticalStrut(12));
            content.add(buildWindowsSection(preferences));
        }

        setLayout(new BorderLayout(12, 12));
        add(content, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(parent);
    }

    private JPanel buildThemeSection(AppPreferences preferences) {
        JPanel section = titledSection("Appearance");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel("Theme:"));
        row.add(Box.createHorizontalStrut(8));

        JComboBox<String> themeCombo = new JComboBox<>(ThemeManager.getAvailableThemeNames());
        themeCombo.setSelectedItem(preferences.getTheme());
        themeCombo.addActionListener(e -> {
            String selected = (String) themeCombo.getSelectedItem();
            if (selected != null && !selected.equals(preferences.getTheme())) {
                if (ThemeManager.applyTheme(selected)) {
                    preferences.setTheme(selected);
                } else {
                    logger.warn("Failed to apply theme {}", selected);
                }
            }
        });
        row.add(themeCombo);
        section.add(row);
        return section;
    }

    private JPanel buildGitSection(AppPreferences preferences) {
        JPanel section = titledSection("Git");
        JCheckBox gitTrackingCheckbox = new JCheckBox(
            "Track ~/.kiro Changes with Git", preferences.isGitTrackingEnabled());
        gitTrackingCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        gitTrackingCheckbox.addActionListener(e -> {
            if (gitTrackingCheckbox.isSelected()) {
                if (!GitAutoCommitter.isGitAvailable()) {
                    JOptionPane.showMessageDialog(this,
                        "git was not found on your PATH. Install it to use this feature.",
                        "Git Not Found", JOptionPane.WARNING_MESSAGE);
                    gitTrackingCheckbox.setSelected(false);
                    return;
                }
                if (!GitAutoCommitter.ensureRepoInitialized(KiroPaths.globalKiroHome())) {
                    JOptionPane.showMessageDialog(this,
                        "Could not set up a git repository in ~/.kiro. Check the logs for details.",
                        "Setup Failed", JOptionPane.WARNING_MESSAGE);
                    gitTrackingCheckbox.setSelected(false);
                    return;
                }
            }
            preferences.setGitTrackingEnabled(gitTrackingCheckbox.isSelected());
        });
        section.add(gitTrackingCheckbox);
        return section;
    }

    private JPanel buildWindowsSection(AppPreferences preferences) {
        JPanel section = titledSection("Windows");
        JCheckBox skipProfileCheckbox = new JCheckBox(
            "Skip PowerShell Profile on Launch", preferences.isSkipPowerShellProfile());
        skipProfileCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        skipProfileCheckbox.addActionListener(e ->
            preferences.setSkipPowerShellProfile(skipProfileCheckbox.isSelected()));
        section.add(skipProfileCheckbox);
        return section;
    }

    private static JPanel titledSection(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }
}

package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.sessions.SessionIndexService;
import com.ourgiant.kirocontrolpanel.sessions.SessionManifest;
import com.ourgiant.kirocontrolpanel.snapshot.SnapshotFrequency;
import com.ourgiant.kirocontrolpanel.snapshot.SnapshotService;
import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.KiroCliSessionDeleter;
import com.ourgiant.kirocontrolpanel.util.KiroSessionLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * App-wide preferences -- theme, git tracking, and (Windows-only) PowerShell
 * profile skip -- previously scattered across a standalone "Config" menu.
 * None of this is per-tab/per-panel configuration, so it lives in its own
 * window off File > Settings... instead. Every control applies immediately,
 * same as the menu items it replaces -- there's no separate Save/Cancel state.
 */
public class SettingsDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);

    public SettingsDialog(Frame parent, AppPreferences preferences, SessionIndexService sessionIndexService) {
        super(parent, "Settings", true);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        content.add(buildThemeSection(preferences));
        content.add(Box.createVerticalStrut(12));
        content.add(buildChangeAlertsSection(preferences));
        content.add(Box.createVerticalStrut(12));
        content.add(buildGitSection(preferences));
        content.add(Box.createVerticalStrut(12));
        content.add(buildSnapshotSection(preferences));
        content.add(Box.createVerticalStrut(12));
        content.add(buildSessionsSection(preferences, sessionIndexService));

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

    private JPanel buildChangeAlertsSection(AppPreferences preferences) {
        JPanel section = titledSection("Change Alerts");
        JCheckBox alertsCheckbox = new JCheckBox(
            "Alert when ~/.kiro changes outside Kiro Control Panel", preferences.isFolderMonitorAlertsEnabled());
        alertsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        alertsCheckbox.addActionListener(e ->
            preferences.setFolderMonitorAlertsEnabled(alertsCheckbox.isSelected()));
        section.add(alertsCheckbox);
        JLabel hint = new JLabel("For a one-off, use File > Pause Change Alerts instead.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));
        hint.setForeground(Color.GRAY);
        section.add(hint);
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

    private JPanel buildSnapshotSection(AppPreferences preferences) {
        JPanel section = titledSection("Snapshots");

        JCheckBox enabledCheckbox = new JCheckBox(
            "Snapshot ~/.kiro Periodically", preferences.isSnapshotEnabled());
        enabledCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel destinationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        destinationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        destinationRow.add(new JLabel("Destination:"));
        destinationRow.add(Box.createHorizontalStrut(8));
        JTextField destinationField = new JTextField(preferences.getSnapshotDestination(), 22);
        destinationField.setEditable(false);
        destinationRow.add(destinationField);
        destinationRow.add(Box.createHorizontalStrut(8));
        JButton browseButton = new JButton("Browse...");
        destinationRow.add(browseButton);

        JPanel frequencyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        frequencyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        frequencyRow.add(new JLabel("Frequency:"));
        frequencyRow.add(Box.createHorizontalStrut(8));
        JComboBox<SnapshotFrequency> frequencyCombo = new JComboBox<>(SnapshotFrequency.values());
        frequencyCombo.setSelectedItem(SnapshotFrequency.closestTo(preferences.getSnapshotFrequencyMinutes()));
        frequencyRow.add(frequencyCombo);

        JPanel retentionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        retentionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        retentionRow.add(new JLabel("Keep Last:"));
        retentionRow.add(Box.createHorizontalStrut(8));
        JComboBox<Integer> retentionCombo = new JComboBox<>(new Integer[] {10, 30, 60, 100});
        retentionCombo.setSelectedItem(preferences.getSnapshotRetentionCount());
        retentionRow.add(retentionCombo);
        retentionRow.add(Box.createHorizontalStrut(4));
        retentionRow.add(new JLabel("snapshots"));

        JLabel lastSnapshotLabel = new JLabel(describeLastSnapshot(preferences));
        lastSnapshotLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lastSnapshotLabel.setFont(lastSnapshotLabel.getFont().deriveFont(lastSnapshotLabel.getFont().getSize2D() - 1f));
        lastSnapshotLabel.setForeground(Color.GRAY);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton snapshotNowButton = new JButton("Snapshot Now");
        actionRow.add(snapshotNowButton);

        JLabel hint = new JLabel("Snapshots exclude extensions/ and sessions/ (Kiro-managed, ephemeral state).");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));
        hint.setForeground(Color.GRAY);

        enabledCheckbox.addActionListener(e -> {
            if (enabledCheckbox.isSelected() && preferences.getSnapshotDestination().isBlank()) {
                JOptionPane.showMessageDialog(this,
                    "Choose a destination folder first.",
                    "No Destination Set", JOptionPane.WARNING_MESSAGE);
                enabledCheckbox.setSelected(false);
                return;
            }
            preferences.setSnapshotEnabled(enabledCheckbox.isSelected());
        });

        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Snapshot Destination");
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            String path = chooser.getSelectedFile().getAbsolutePath();
            preferences.setSnapshotDestination(path);
            destinationField.setText(path);
            lastSnapshotLabel.setText(describeLastSnapshot(preferences));
        });

        frequencyCombo.addActionListener(e -> {
            SnapshotFrequency selected = (SnapshotFrequency) frequencyCombo.getSelectedItem();
            if (selected != null) {
                preferences.setSnapshotFrequencyMinutes(selected.minutes());
            }
        });

        retentionCombo.addActionListener(e -> {
            Integer selected = (Integer) retentionCombo.getSelectedItem();
            if (selected != null) {
                preferences.setSnapshotRetentionCount(selected);
            }
        });

        snapshotNowButton.addActionListener(e -> {
            String destination = preferences.getSnapshotDestination();
            if (destination.isBlank()) {
                JOptionPane.showMessageDialog(this,
                    "Choose a destination folder first.",
                    "No Destination Set", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Path destinationDir = Paths.get(destination);
            snapshotNowButton.setEnabled(false);
            snapshotNowButton.setText("Snapshotting...");
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws IOException {
                    SnapshotService.createSnapshot(KiroPaths.globalKiroHome(), destinationDir);
                    SnapshotService.pruneOldSnapshots(destinationDir, preferences.getSnapshotRetentionCount());
                    return null;
                }

                @Override
                protected void done() {
                    snapshotNowButton.setEnabled(true);
                    snapshotNowButton.setText("Snapshot Now");
                    try {
                        get();
                        lastSnapshotLabel.setText(describeLastSnapshot(preferences));
                    } catch (Exception ex) {
                        logger.warn("Manual .kiro snapshot failed", ex);
                        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Snapshot failed: " + detail,
                            "Snapshot Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        });

        section.add(enabledCheckbox);
        section.add(Box.createVerticalStrut(6));
        section.add(destinationRow);
        section.add(Box.createVerticalStrut(6));
        section.add(frequencyRow);
        section.add(Box.createVerticalStrut(6));
        section.add(retentionRow);
        section.add(Box.createVerticalStrut(6));
        section.add(actionRow);
        section.add(lastSnapshotLabel);
        section.add(Box.createVerticalStrut(4));
        section.add(hint);
        return section;
    }

    private static String describeLastSnapshot(AppPreferences preferences) {
        String destination = preferences.getSnapshotDestination();
        if (destination.isBlank()) {
            return "No destination chosen yet.";
        }
        Optional<Instant> latest = SnapshotService.latestSnapshotTime(Paths.get(destination));
        if (latest.isEmpty()) {
            return "No snapshot taken yet.";
        }
        String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(latest.get());
        return "Last snapshot: " + formatted;
    }

    private JPanel buildSessionsSection(AppPreferences preferences, SessionIndexService sessionIndexService) {
        JPanel section = titledSection("Sessions");

        JCheckBox enabledCheckbox = new JCheckBox(
            "Index kiro-cli Session History", preferences.isSessionsIndexingEnabled());
        enabledCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        enabledCheckbox.addActionListener(e ->
            preferences.setSessionsIndexingEnabled(enabledCheckbox.isSelected()));

        JPanel locationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        locationRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        locationRow.add(new JLabel("Index Location:"));
        locationRow.add(Box.createHorizontalStrut(8));
        JTextField locationField = new JTextField(describeIndexLocation(preferences), 22);
        locationField.setEditable(false);
        locationRow.add(locationField);
        locationRow.add(Box.createHorizontalStrut(8));
        JButton browseButton = new JButton("Browse...");
        locationRow.add(browseButton);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton rebuildButton = new JButton("Rebuild Index");
        actionRow.add(rebuildButton);
        JButton cleanupButton = new JButton("Clean Up Empty Sessions");
        actionRow.add(cleanupButton);

        JLabel locationHint = new JLabel("Changing the location takes effect after restarting the app.");
        locationHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        locationHint.setFont(locationHint.getFont().deriveFont(locationHint.getFont().getSize2D() - 1f));
        locationHint.setForeground(Color.GRAY);

        JLabel hint = new JLabel("Never written under ~/.kiro -- lives in this app's own data directory.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));
        hint.setForeground(Color.GRAY);

        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Sessions Index Directory");
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path dbFile = chooser.getSelectedFile().toPath().resolve("sessions-index.db");
            preferences.setSessionsIndexLocation(dbFile.toString());
            locationField.setText(describeIndexLocation(preferences));
        });

        rebuildButton.addActionListener(e -> {
            rebuildButton.setEnabled(false);
            rebuildButton.setText("Rebuilding...");
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws IOException, SQLException {
                    sessionIndexService.rebuildIndex(KiroPaths.globalSessionsCliDir());
                    return null;
                }

                @Override
                protected void done() {
                    rebuildButton.setEnabled(true);
                    rebuildButton.setText("Rebuild Index");
                    try {
                        get();
                    } catch (Exception ex) {
                        logger.warn("Sessions index rebuild failed", ex);
                        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Rebuild failed: " + detail,
                            "Rebuild Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        });

        cleanupButton.addActionListener(e -> onCleanUpEmptySessions(sessionIndexService, cleanupButton));

        section.add(enabledCheckbox);
        section.add(Box.createVerticalStrut(6));
        section.add(locationRow);
        section.add(Box.createVerticalStrut(4));
        section.add(locationHint);
        section.add(Box.createVerticalStrut(6));
        section.add(actionRow);
        section.add(Box.createVerticalStrut(4));
        section.add(hint);
        return section;
    }

    private static String describeIndexLocation(AppPreferences preferences) {
        String override = preferences.getSessionsIndexLocation();
        return override.isBlank() ? "(default) ~/.kiro-control-panel/sessions-index.db" : override;
    }

    /** Finds sessions with no title and no recorded messages ({@link SessionManifest#isEmpty()})
     * -- shown as "(no prompt)" in the Sessions tab -- confirms with the user, then deletes each
     * one via {@link KiroCliSessionDeleter} (the same mechanism issue #124's fix uses) and
     * removes it from the index immediately rather than waiting for the file watcher. See #126. */
    private void onCleanUpEmptySessions(SessionIndexService sessionIndexService, JButton cleanupButton) {
        List<SessionManifest> emptySessions;
        try {
            emptySessions = sessionIndexService.listAll().stream().filter(SessionManifest::isEmpty).toList();
        } catch (SQLException ex) {
            logger.warn("Failed to check for empty sessions", ex);
            JOptionPane.showMessageDialog(this,
                "Couldn't check for empty sessions: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (emptySessions.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No empty sessions found.", "Clean Up Empty Sessions", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirmed = JOptionPane.showConfirmDialog(this,
            "Found " + emptySessions.size() + " empty session(s) (no prompt, no messages).\n"
                + "Delete them from kiro-cli?",
            "Clean Up Empty Sessions", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }

        cleanupButton.setEnabled(false);
        cleanupButton.setText("Cleaning Up...");
        SwingWorker<int[], Void> worker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() {
                int deleted = 0;
                for (SessionManifest session : emptySessions) {
                    if (KiroCliSessionDeleter.delete(session.sessionId())) {
                        deleted++;
                        try {
                            sessionIndexService.removeSession(session.sessionId());
                        } catch (SQLException ex) {
                            logger.warn("Deleted session {} but failed to remove it from the index",
                                session.sessionId(), ex);
                        }
                    }
                }
                return new int[] {deleted, emptySessions.size()};
            }

            @Override
            protected void done() {
                cleanupButton.setEnabled(true);
                cleanupButton.setText("Clean Up Empty Sessions");
                try {
                    int[] result = get();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                        "Deleted " + result[0] + " of " + result[1] + " empty sessions.",
                        "Clean Up Empty Sessions", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    logger.warn("Empty session clean-up failed", ex);
                    String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                        "Clean-up failed: " + detail,
                        "Clean-up Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
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

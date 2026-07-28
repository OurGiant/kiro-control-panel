package com.ourgiant.kirocontrolpanel.changelog;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.InAppFileEditorLauncher;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Plain-JPanel content of {@link ChangeLogDialog} -- split out for the same
 * reason every other {@code *Panel}/{@code *Dialog} pair in this app is:
 * {@code JDialog} throws {@code HeadlessException} in this project's build
 * container, a plain {@code JPanel} doesn't, so this stays unit-testable.
 * Owns no file I/O itself -- {@link #setEntries} is handed already-loaded data.
 */
public class ChangeLogPanel extends JPanel {
    private static final String[] PRESETS = {
        "Last 1 day", "Last 1 week", "Last 2 weeks", "Last 1 month", "Last 3 months"
    };
    private static final String DEFAULT_PRESET = "Last 1 week";

    private final JComboBox<String> presetCombo = new JComboBox<>(PRESETS);
    private final ChangeLogTableModel tableModel = new ChangeLogTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton openFileButton;

    public ChangeLogPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Show:"));
        presetCombo.setSelectedItem(DEFAULT_PRESET);
        filterPanel.add(presetCombo);
        add(filterPanel, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        openFileButton = new JButton("Edit...");
        openFileButton.setEnabled(false);
        openFileButton.addActionListener(e -> onOpenFile());
        add(SwingLayoutUtils.createVerticalButtonPanel(openFileButton), BorderLayout.EAST);
    }

    public void setEntries(List<ChangeLogEntry> entries) {
        tableModel.setEntries(entries);
        updateButtonState();
    }

    /** The cutoff instant implied by the currently selected preset, recomputed against "now" each call. */
    public Instant selectedCutoff() {
        return cutoffFor((String) presetCombo.getSelectedItem());
    }

    public void addFilterChangeListener(Runnable listener) {
        presetCombo.addActionListener(e -> listener.run());
    }

    /** Package-private, for tests. */
    JButton getOpenFileButton() {
        return openFileButton;
    }

    /** Package-private, for tests. */
    JTable getTable() {
        return table;
    }

    /** Package-private, for tests. */
    JComboBox<String> getPresetCombo() {
        return presetCombo;
    }

    private void updateButtonState() {
        openFileButton.setEnabled(table.getSelectedRow() >= 0);
    }

    private void onOpenFile() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            ChangeLogEntry entry = tableModel.entryAt(row);
            WorkspaceScope scope = entry.global()
                ? WorkspaceScope.global()
                : new WorkspaceScope(entry.scopeLabel(), Paths.get(entry.scopeLabel()));
            InAppFileEditorLauncher.open(InAppFileEditorLauncher.resolveFrame(this),
                entry.surfaceEnum(), scope, entry.path());
        }
    }

    static Instant cutoffFor(String preset) {
        ZonedDateTime now = ZonedDateTime.now();
        return switch (preset) {
            case "Last 1 day" -> now.minusDays(1).toInstant();
            case "Last 2 weeks" -> now.minusWeeks(2).toInstant();
            case "Last 1 month" -> now.minusMonths(1).toInstant();
            case "Last 3 months" -> now.minusMonths(3).toInstant();
            default -> now.minusWeeks(1).toInstant(); // "Last 1 week"
        };
    }
}

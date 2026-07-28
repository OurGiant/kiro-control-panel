package com.ourgiant.kirocontrolpanel.changelog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.List;

/**
 * Thin {@code JDialog} chrome around {@link ChangeLogPanel} -- non-modal,
 * auto-refreshing on a timer while open, mirroring {@code LogViewerDialog}'s
 * exact template. See issue #79.
 */
public class ChangeLogDialog extends JDialog {
    private static final int REFRESH_INTERVAL_MILLIS = 2000;

    private final ChangeLogPanel panel = new ChangeLogPanel();
    private final Timer refreshTimer;
    private Instant lastLoadedCutoff;
    private int lastLoadedCount = -1;

    public ChangeLogDialog(Frame parent) {
        super(parent, "Kiro Change Log", false);

        setLayout(new BorderLayout(8, 8));
        add(panel, BorderLayout.CENTER);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> reload(true));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        panel.addFilterChangeListener(() -> reload(true));

        refreshTimer = new Timer(REFRESH_INTERVAL_MILLIS, e -> reload(false));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTimer.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });

        setPreferredSize(new Dimension(820, 500));
        reload(true);
        pack();
        setLocationRelativeTo(parent);
    }

    /** @param force reload even if the result looks unchanged (used for the explicit Refresh button and filter changes) */
    private void reload(boolean force) {
        Instant cutoff = panel.selectedCutoff();
        List<ChangeLogEntry> entries = ChangeLogService.loadSince(cutoff);
        if (!force && cutoff.equals(lastLoadedCutoff) && entries.size() == lastLoadedCount) {
            return; // nothing new since the last tick, skip the re-render
        }
        lastLoadedCutoff = cutoff;
        lastLoadedCount = entries.size();
        panel.setEntries(entries);
    }
}

package com.ourgiant.kirocontrolpanel.diagnostics;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Thin {@code JDialog} chrome around {@link KiroSetupFindingsPanel} --
 * non-modal, matching {@code McpCatalogDialog}'s reasoning: the user may
 * want to switch to a panel and fix something by hand while this stays
 * open. Findings are computed by the caller and handed in already-scanned,
 * same as every local-file-op panel in this app (no SwingWorker needed for
 * a fast local disk scan). {@code rescanner} re-runs that same scan on
 * demand (issue #86) -- e.g. after fixing something by hand through
 * "Edit..." (#82/#84), which the "Fix..." button's own list-item removal
 * can't detect on its own.
 */
public class KiroSetupScanDialog extends JDialog {

    public KiroSetupScanDialog(Frame parent, List<Finding> findings, Supplier<List<Finding>> rescanner) {
        super(parent, "Kiro Setup Scan", false);

        setLayout(new BorderLayout(8, 8));

        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        panel.setFindings(findings);
        panel.addRescanListener(() -> panel.setFindings(rescanner.get()));
        add(panel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        setPreferredSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(parent);
    }
}

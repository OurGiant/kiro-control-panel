package com.ourgiant.kirocontrolpanel.diagnostics;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Thin {@code JDialog} chrome around {@link KiroSetupFindingsPanel} --
 * non-modal, matching {@code McpCatalogDialog}'s reasoning: the user may
 * want to switch to a panel and fix something by hand while this stays
 * open. Findings are computed by the caller and handed in already-scanned,
 * same as every local-file-op panel in this app (no SwingWorker needed for
 * a fast local disk scan).
 */
public class KiroSetupScanDialog extends JDialog {

    public KiroSetupScanDialog(Frame parent, List<Finding> findings) {
        super(parent, "Kiro Setup Scan", false);

        setLayout(new BorderLayout(8, 8));

        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        panel.setFindings(findings);
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

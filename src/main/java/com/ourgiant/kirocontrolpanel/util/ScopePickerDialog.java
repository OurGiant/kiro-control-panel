package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Lets the user pick a destination scope for a "Copy to..." action -- any
 * pinned workspace (or Global, where the surface supports it) other than
 * the one the item currently lives in. {@link WorkspaceScope#toString()}
 * already renders as the scope's label, so the combo needs no custom
 * renderer; unlike {@code WorkspaceScopeBar} this doesn't truncate long
 * pinned-workspace paths -- an acceptable v1 limitation for a one-off
 * picker rather than a persistent bar.
 */
public class ScopePickerDialog extends JDialog {
    private final JComboBox<WorkspaceScope> scopeCombo;
    private boolean confirmed = false;

    public ScopePickerDialog(Frame parent, String itemLabel, List<WorkspaceScope> availableScopes) {
        super(parent, "Copy to...", true);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(new JLabel("Copy \"" + itemLabel + "\" to:"), BorderLayout.NORTH);

        scopeCombo = new JComboBox<>(availableScopes.toArray(new WorkspaceScope[0]));
        add(scopeCombo, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(copyButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(copyButton);

        pack();
        setLocationRelativeTo(parent);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public WorkspaceScope getSelectedScope() {
        return (WorkspaceScope) scopeCombo.getSelectedItem();
    }
}

package com.ourgiant.kirocontrolpanel.hooks;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Dialog chrome (title, Save/Cancel, validation) around {@link HookFormPanel}.
 * Only mutates the in-memory {@link Hook} passed in; the caller persists its
 * containing file after {@link #isSaved()}.
 */
public class HookEditDialog extends JDialog {
    private final Hook hook;
    private final HookFormPanel formPanel = new HookFormPanel();

    private boolean saved = false;

    public HookEditDialog(Frame parent, Hook hook, boolean isNew) {
        super(parent, isNew ? "Add Hook" : "Edit Hook: " + hook.getName(), true);
        this.hook = hook;

        initializeUI();
        formPanel.populateFrom(hook);
        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(parent);
    }

    public boolean isSaved() {
        return saved;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(formPanel, BorderLayout.CENTER);

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

    private void onSave() {
        if (!formPanel.syncFromActiveTabIfNeeded()) {
            return;
        }

        String name = formPanel.getHookName();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hook name is required.", "Missing Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (formPanel.isCommandType() && formPanel.getCommandText().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Command is required for a shell command action.", "Missing Command", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!formPanel.isCommandType() && formPanel.getPromptText().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Prompt is required for an agent prompt action.", "Missing Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Hook built = formPanel.buildHook();
        hook.setName(built.getName());
        hook.setTrigger(built.getTrigger());
        hook.setMatcher(built.getMatcher());
        hook.setEnabled(built.isEnabled());
        hook.setTimeout(built.getTimeout());
        hook.setAction(built.getAction());

        saved = true;
        setVisible(false);
    }
}

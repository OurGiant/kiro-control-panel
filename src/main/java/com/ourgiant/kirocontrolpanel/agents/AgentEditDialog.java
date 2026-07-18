package com.ourgiant.kirocontrolpanel.agents;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Dialog chrome (title, Save/Cancel) around {@link AgentFormPanel}. Only
 * mutates the in-memory {@link AgentConfig} passed in; the caller persists
 * it to disk after {@link #isSaved()}. No required-field validation --
 * unlike MCP servers/hooks, every field in Kiro's agent schema is optional.
 */
public class AgentEditDialog extends JDialog {
    private final AgentConfig agent;
    private final AgentFormPanel formPanel = new AgentFormPanel();

    private boolean saved = false;

    public AgentEditDialog(Frame parent, AgentConfig agent, boolean isNew) {
        super(parent, isNew ? "Add Agent" : "Edit Agent: " + agent.getFileName(), true);
        this.agent = agent;

        initializeUI();
        formPanel.populateFrom(agent);
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

        AgentConfig built = formPanel.buildAgent();
        agent.setDescription(built.getDescription());
        agent.setPrompt(built.getPrompt());
        agent.setModel(built.getModel());
        agent.setKeyboardShortcut(built.getKeyboardShortcut());
        agent.setWelcomeMessage(built.getWelcomeMessage());
        agent.setTools(built.getTools());
        agent.setAllowedTools(built.getAllowedTools());
        agent.setIncludeMcpJson(built.getIncludeMcpJson());
        agent.getExtra().clear();
        agent.getExtra().putAll(built.getExtra());

        saved = true;
        setVisible(false);
    }
}

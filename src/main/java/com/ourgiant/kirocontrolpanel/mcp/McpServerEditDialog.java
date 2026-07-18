package com.ourgiant.kirocontrolpanel.mcp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Dialog chrome (title, Save/Cancel, config-level validation) around
 * {@link McpServerFormPanel}. Only mutates the in-memory {@link McpConfigFile}
 * passed in; the caller persists it to disk after {@link #isSaved()}.
 */
public class McpServerEditDialog extends JDialog {
    private final McpConfigFile config;
    private final String originalName; // null when adding a new server
    private final McpServerFormPanel formPanel = new McpServerFormPanel();

    private boolean saved = false;

    public McpServerEditDialog(Frame parent, McpConfigFile config, String serverName) {
        super(parent, serverName == null ? "Add MCP Server" : "Edit MCP Server: " + serverName, true);
        this.config = config;
        this.originalName = serverName;

        initializeUI();
        if (serverName != null) {
            formPanel.setServerName(serverName);
            formPanel.populateFrom(config.getMcpServers().get(serverName));
        } else {
            formPanel.setDefaultsForNewServer();
        }
        pack();
        setMinimumSize(new Dimension(560, getHeight()));
        setLocationRelativeTo(parent);
    }

    /**
     * Add mode, pre-filled from an externally-built config (e.g. a catalog
     * entry) rather than left at defaults. Still an add, not an edit --
     * {@code originalName} stays null so the duplicate-name check in
     * {@link #onSave()} runs against {@code suggestedName} like any other
     * new server, and the user can rename before saving.
     */
    public McpServerEditDialog(Frame parent, McpConfigFile config, String suggestedName, McpServerConfig prefill) {
        super(parent, "Add MCP Server", true);
        this.config = config;
        this.originalName = null;

        initializeUI();
        formPanel.setServerName(suggestedName);
        formPanel.populateFrom(prefill);
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

        String name = formPanel.getServerName();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Server name is required.", "Missing Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean isRename = originalName != null && !originalName.equals(name);
        boolean isNewName = originalName == null || isRename;
        if (isNewName && config.getMcpServers().containsKey(name)) {
            JOptionPane.showMessageDialog(this,
                "A server named \"" + name + "\" already exists.", "Duplicate Name", JOptionPane.WARNING_MESSAGE);
            return;
        }

        McpServerConfig server = formPanel.buildServer();
        if (formPanel.isLocalType() && server.getCommand() == null) {
            JOptionPane.showMessageDialog(this,
                "Command is required for a local server.", "Missing Command", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!formPanel.isLocalType() && server.getUrl() == null) {
            JOptionPane.showMessageDialog(this,
                "URL is required for a remote server.", "Missing URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isRename) {
            config.getMcpServers().remove(originalName);
        }
        config.getMcpServers().put(name, server);

        saved = true;
        setVisible(false);
    }
}

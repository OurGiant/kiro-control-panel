package com.ourgiant.kirocontrolpanel.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The name field plus Form/Raw JSON tabs for editing one MCP server entry —
 * everything {@link McpServerEditDialog} needs except the dialog chrome
 * (title, Save/Cancel buttons, and config-level validation like duplicate
 * name checking, which stays in the dialog since it needs the containing
 * {@link McpConfigFile}). Split out from the dialog specifically so this can
 * be unit tested: JDialog can't be constructed in a headless environment,
 * but a plain JPanel can.
 */
public class McpServerFormPanel extends JPanel {
    private static final String TYPE_LOCAL = "Local (command)";
    private static final String TYPE_REMOTE = "Remote (url)";
    private static final int FORM_TAB = 0;
    private static final int RAW_JSON_TAB = 1;

    private static final ObjectMapper RAW_MAPPER = JsonMapperFactory.createMapper();
    private static final PrettyPrinter RAW_PRINTER = JsonMapperFactory.createPrettyPrinter();

    private JTabbedPane tabbedPane;
    private JTextArea rawJsonArea;
    private int lastSelectedTabIndex = FORM_TAB;
    private boolean handlingTabChange = false;

    private JTextField nameField;
    private JComboBox<String> typeCombo;
    private JTextField commandField;
    private JTextArea argsArea;
    private JTextArea envArea;
    private JTextField urlField;
    private JTextArea headersArea;
    private JTextField oauthClientIdField;
    private JTextField oauthScopesField;
    private JCheckBox enabledCheckBox;
    private JTextField autoApproveField;
    private JTextField disabledToolsField;

    public McpServerFormPanel() {
        super(new BorderLayout());
        initializeUI();
    }

    public String getServerName() {
        return nameField.getText().trim();
    }

    public void setServerName(String name) {
        nameField.setText(name);
    }

    public boolean isLocalType() {
        return TYPE_LOCAL.equals(typeCombo.getSelectedItem());
    }

    /** Sets sensible defaults for a brand-new server (as opposed to editing an existing one). */
    public void setDefaultsForNewServer() {
        typeCombo.setSelectedItem(TYPE_LOCAL);
        enabledCheckBox.setSelected(true);
        updateFieldAvailability();
    }

    /** If the Raw JSON tab is active, parses it into the form first. @return false (and shows an error) if it doesn't parse. */
    public boolean syncFromActiveTabIfNeeded() {
        return tabbedPane.getSelectedIndex() != RAW_JSON_TAB || syncRawToFormShowingErrors();
    }

    /** Builds a server object from the Form tab's current fields, without validating required ones. */
    public McpServerConfig buildServer() {
        McpServerConfig server = new McpServerConfig();
        boolean local = isLocalType();
        if (local) {
            String command = commandField.getText().trim();
            server.setCommand(command.isEmpty() ? null : command);
            List<String> args = parseLines(argsArea.getText());
            server.setArgs(args.isEmpty() ? null : args);
            Map<String, String> env = parseMap(envArea.getText());
            server.setEnv(env.isEmpty() ? null : env);
        } else {
            String url = urlField.getText().trim();
            server.setUrl(url.isEmpty() ? null : url);
            Map<String, String> headers = parseMap(headersArea.getText());
            server.setHeaders(headers.isEmpty() ? null : headers);
            String clientId = oauthClientIdField.getText().trim();
            if (!clientId.isEmpty()) {
                Map<String, Object> oauth = new LinkedHashMap<>();
                oauth.put("clientId", clientId);
                server.setOauth(oauth);
            }
            List<String> scopes = parseCsv(oauthScopesField.getText());
            server.setOauthScopes(scopes.isEmpty() ? null : scopes);
        }
        server.setDisabled(!enabledCheckBox.isSelected());
        List<String> autoApprove = parseCsv(autoApproveField.getText());
        server.setAutoApprove(autoApprove.isEmpty() ? null : autoApprove);
        List<String> disabledTools = parseCsv(disabledToolsField.getText());
        server.setDisabledTools(disabledTools.isEmpty() ? null : disabledTools);
        return server;
    }

    public void populateFrom(McpServerConfig server) {
        typeCombo.setSelectedItem(server.isRemote() ? TYPE_REMOTE : TYPE_LOCAL);
        commandField.setText(server.getCommand() == null ? "" : server.getCommand());
        argsArea.setText(server.getArgs() == null ? "" : String.join("\n", server.getArgs()));
        envArea.setText(joinMap(server.getEnv()));
        urlField.setText(server.getUrl() == null ? "" : server.getUrl());
        headersArea.setText(joinMap(server.getHeaders()));
        Object clientId = server.getOauth() == null ? null : server.getOauth().get("clientId");
        oauthClientIdField.setText(clientId == null ? "" : String.valueOf(clientId));
        oauthScopesField.setText(server.getOauthScopes() == null ? "" : String.join(", ", server.getOauthScopes()));
        enabledCheckBox.setSelected(!server.isDisabled());
        autoApproveField.setText(server.getAutoApprove() == null ? "" : String.join(", ", server.getAutoApprove()));
        disabledToolsField.setText(server.getDisabledTools() == null ? "" : String.join(", ", server.getDisabledTools()));
        updateFieldAvailability();
    }

    // Package-private, for tests: drive tab switches (which is what triggers the
    // sync logic) and inspect/edit the Raw JSON tab's text directly.
    void selectFormTab() {
        tabbedPane.setSelectedIndex(FORM_TAB);
    }

    void selectRawJsonTab() {
        tabbedPane.setSelectedIndex(RAW_JSON_TAB);
    }

    String getRawJsonText() {
        return rawJsonArea.getText();
    }

    int getSelectedTabIndex() {
        return tabbedPane.getSelectedIndex();
    }

    void setRawJsonText(String text) {
        rawJsonArea.setText(text);
    }

    private void initializeUI() {
        nameField = new JTextField();
        JPanel namePanel = new JPanel(new GridBagLayout());
        GridBagConstraints nameGbc = new GridBagConstraints();
        nameGbc.insets = new Insets(3, 6, 3, 6);
        nameGbc.gridx = 0;
        nameGbc.gridy = 0;
        namePanel.add(new JLabel("Server Name:"), nameGbc);
        nameGbc.gridx = 1;
        nameGbc.fill = GridBagConstraints.HORIZONTAL;
        nameGbc.weightx = 1.0;
        namePanel.add(nameField, nameGbc);
        add(namePanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Form", createFormTab());
        tabbedPane.addTab("Raw JSON", createRawJsonTab());
        tabbedPane.addChangeListener(e -> onTabChanged());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private JScrollPane createFormTab() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        int row = 0;

        typeCombo = new JComboBox<>(new String[] {TYPE_LOCAL, TYPE_REMOTE});
        typeCombo.addActionListener(e -> updateFieldAvailability());
        row = addRow(form, gbc, row, "Type:", typeCombo, false);

        commandField = new JTextField();
        commandField.setToolTipText("Executable to launch this MCP server, e.g. npx or uvx");
        row = addRow(form, gbc, row, "Command:", commandField, false);

        argsArea = new JTextArea(3, 20);
        argsArea.setToolTipText("One argument per line");
        row = addRow(form, gbc, row, "Args:", scroll(argsArea), true);

        envArea = new JTextArea(3, 20);
        envArea.setToolTipText("One KEY=VALUE per line; values may reference ${VAR}");
        row = addRow(form, gbc, row, "Env:", scroll(envArea), true);

        urlField = new JTextField();
        urlField.setToolTipText("Remote MCP endpoint, e.g. https://example.com/mcp");
        row = addRow(form, gbc, row, "URL:", urlField, false);

        headersArea = new JTextArea(3, 20);
        headersArea.setToolTipText("One KEY=VALUE header per line");
        row = addRow(form, gbc, row, "Headers:", scroll(headersArea), true);

        oauthClientIdField = new JTextField();
        row = addRow(form, gbc, row, "OAuth Client ID:", oauthClientIdField, false);

        oauthScopesField = new JTextField();
        oauthScopesField.setToolTipText("Comma-separated OAuth scopes");
        row = addRow(form, gbc, row, "OAuth Scopes:", oauthScopesField, false);

        enabledCheckBox = new JCheckBox("Enabled");
        row = addRow(form, gbc, row, "", enabledCheckBox, false);

        autoApproveField = new JTextField();
        autoApproveField.setToolTipText("Comma-separated tool names to auto-approve, or * for all");
        row = addRow(form, gbc, row, "Auto Approve:", autoApproveField, false);

        disabledToolsField = new JTextField();
        disabledToolsField.setToolTipText("Comma-separated tool names to omit from the agent");
        addRow(form, gbc, row, "Disabled Tools:", disabledToolsField, false);

        return new JScrollPane(form);
    }

    private JScrollPane createRawJsonTab() {
        rawJsonArea = new JTextArea();
        rawJsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return new JScrollPane(rawJsonArea);
    }

    /**
     * Keeps the Form and Raw JSON tabs in sync with each other whenever the
     * user switches between them, so Save can always just read the Form
     * fields regardless of which tab was last edited.
     */
    private void onTabChanged() {
        if (handlingTabChange) {
            return;
        }
        handlingTabChange = true;
        try {
            int newIndex = tabbedPane.getSelectedIndex();
            if (lastSelectedTabIndex == FORM_TAB && newIndex == RAW_JSON_TAB) {
                try {
                    rawJsonArea.setText(RAW_MAPPER.writer(RAW_PRINTER).writeValueAsString(buildServer()));
                    rawJsonArea.setCaretPosition(0);
                } catch (JsonProcessingException ignored) {
                    // Form fields always produce valid JSON; nothing to recover from.
                }
                lastSelectedTabIndex = newIndex;
            } else if (lastSelectedTabIndex == RAW_JSON_TAB && newIndex == FORM_TAB) {
                if (syncRawToFormShowingErrors()) {
                    lastSelectedTabIndex = newIndex;
                } else {
                    tabbedPane.setSelectedIndex(RAW_JSON_TAB);
                    lastSelectedTabIndex = RAW_JSON_TAB;
                }
            } else {
                lastSelectedTabIndex = newIndex;
            }
        } finally {
            handlingTabChange = false;
        }
    }

    /** Wraps {@link #tryParseRawJsonIntoForm()} with the user-facing error dialog. */
    private boolean syncRawToFormShowingErrors() {
        String error = tryParseRawJsonIntoForm();
        if (error == null) {
            return true;
        }
        JOptionPane.showMessageDialog(this,
            "That doesn't parse as a valid server object:\n\n" + error, "Invalid JSON", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    /**
     * Package-private, for tests: parses the Raw JSON tab's text and applies it to
     * the form fields, without ever showing a dialog (JOptionPane needs a real
     * display, which isn't available in a headless test run).
     *
     * @return null on success, or the parse error message on failure
     */
    String tryParseRawJsonIntoForm() {
        try {
            populateFrom(RAW_MAPPER.readValue(rawJsonArea.getText(), McpServerConfig.class));
            return null;
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    private static JScrollPane scroll(JTextArea area) {
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return new JScrollPane(area);
    }

    private int addRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field, boolean grows) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = grows ? 1.0 : 0;
        panel.add(field, gbc);
        return row + 1;
    }

    private void updateFieldAvailability() {
        boolean local = isLocalType();
        commandField.setEnabled(local);
        argsArea.setEnabled(local);
        envArea.setEnabled(local);
        urlField.setEnabled(!local);
        headersArea.setEnabled(!local);
        oauthClientIdField.setEnabled(!local);
        oauthScopesField.setEnabled(!local);
    }

    private static String joinMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static Map<String, String> parseMap(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                continue;
            }
            result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return result;
    }

    private static List<String> parseLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> parseCsv(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}

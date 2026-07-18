package com.ourgiant.kirocontrolpanel.agents;

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
 * The Form/Raw JSON tabs for editing one agent -- everything
 * {@link AgentEditDialog} needs except the dialog chrome (title,
 * Save/Cancel). Split out from the dialog specifically so this can be unit
 * tested: JDialog can't be constructed in a headless environment, but a
 * plain JPanel can.
 * <p>
 * Only description/prompt/model/keyboardShortcut/welcomeMessage/tools/
 * allowedTools/includeMcpJson get dedicated Form fields -- everything else
 * Kiro's schema allows (name, mcpServers, toolAliases, toolsSettings,
 * resources, hooks) is Raw-JSON-tab-only, carried through via
 * {@link AgentConfig}'s extra catch-all so a Form-tab-only edit never
 * destroys it.
 */
public class AgentFormPanel extends JPanel {
    private static final int FORM_TAB = 0;
    private static final int RAW_JSON_TAB = 1;

    private static final ObjectMapper RAW_MAPPER = JsonMapperFactory.createMapper();
    private static final PrettyPrinter RAW_PRINTER = JsonMapperFactory.createPrettyPrinter();

    private JTabbedPane tabbedPane;
    private JTextArea rawJsonArea;
    private int lastSelectedTabIndex = FORM_TAB;
    private boolean handlingTabChange = false;

    private JTextField descriptionField;
    private JTextArea promptArea;
    private JTextField modelField;
    private JTextField keyboardShortcutField;
    private JTextArea welcomeMessageArea;
    private JTextField toolsField;
    private JTextField allowedToolsField;
    private JCheckBox includeMcpJsonCheckBox;

    private Map<String, Object> lastKnownExtra = new LinkedHashMap<>();

    public AgentFormPanel() {
        super(new BorderLayout());
        initializeUI();
    }

    /** If the Raw JSON tab is active, parses it into the form first. @return false (and shows an error) if it doesn't parse. */
    public boolean syncFromActiveTabIfNeeded() {
        return tabbedPane.getSelectedIndex() != RAW_JSON_TAB || syncRawToFormShowingErrors();
    }

    public void populateFrom(AgentConfig source) {
        descriptionField.setText(source.getDescription() == null ? "" : source.getDescription());
        promptArea.setText(source.getPrompt() == null ? "" : source.getPrompt());
        modelField.setText(source.getModel() == null ? "" : source.getModel());
        keyboardShortcutField.setText(source.getKeyboardShortcut() == null ? "" : source.getKeyboardShortcut());
        welcomeMessageArea.setText(source.getWelcomeMessage() == null ? "" : source.getWelcomeMessage());
        toolsField.setText(joinCsv(source.getTools()));
        allowedToolsField.setText(joinCsv(source.getAllowedTools()));
        includeMcpJsonCheckBox.setSelected(Boolean.TRUE.equals(source.getIncludeMcpJson()));
        lastKnownExtra = new LinkedHashMap<>(source.getExtra());
    }

    /**
     * Builds an identity-less agent from the Form tab's current fields,
     * carrying forward whatever extra (Raw-JSON-only) fields were last
     * parsed or populated -- switching to the Form tab and back must never
     * silently drop mcpServers/toolAliases/toolsSettings/resources/hooks.
     */
    public AgentConfig buildAgent() {
        AgentConfig built = new AgentConfig(null, null);
        String description = descriptionField.getText().trim();
        built.setDescription(description.isEmpty() ? null : description);
        String prompt = promptArea.getText().trim();
        built.setPrompt(prompt.isEmpty() ? null : prompt);
        String model = modelField.getText().trim();
        built.setModel(model.isEmpty() ? null : model);
        String keyboardShortcut = keyboardShortcutField.getText().trim();
        built.setKeyboardShortcut(keyboardShortcut.isEmpty() ? null : keyboardShortcut);
        String welcomeMessage = welcomeMessageArea.getText().trim();
        built.setWelcomeMessage(welcomeMessage.isEmpty() ? null : welcomeMessage);
        List<String> tools = parseCsv(toolsField.getText());
        built.setTools(tools.isEmpty() ? null : tools);
        List<String> allowedTools = parseCsv(allowedToolsField.getText());
        built.setAllowedTools(allowedTools.isEmpty() ? null : allowedTools);
        built.setIncludeMcpJson(includeMcpJsonCheckBox.isSelected() ? Boolean.TRUE : null);
        for (Map.Entry<String, Object> entry : lastKnownExtra.entrySet()) {
            built.putExtra(entry.getKey(), entry.getValue());
        }
        return built;
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

    void setRawJsonText(String text) {
        rawJsonArea.setText(text);
    }

    int getSelectedTabIndex() {
        return tabbedPane.getSelectedIndex();
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
            populateFrom(RAW_MAPPER.readerForUpdating(new AgentConfig(null, null))
                .readValue(rawJsonArea.getText(), AgentConfig.class));
            return null;
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    private void initializeUI() {
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Form", createFormTab());
        tabbedPane.addTab("Raw JSON", createRawJsonTab());
        tabbedPane.addChangeListener(e -> onTabChanged());
        add(tabbedPane, BorderLayout.CENTER);
    }

    private JScrollPane createFormTab() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        int row = 0;

        descriptionField = new JTextField();
        row = addRow(form, gbc, row, "Description:", descriptionField, false);

        promptArea = new JTextArea(4, 20);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setToolTipText("Inline text, or a file:// URI");
        row = addRow(form, gbc, row, "Prompt:", new JScrollPane(promptArea), true);

        modelField = new JTextField();
        modelField.setToolTipText("Model ID, e.g. claude-sonnet-4; blank uses the default model");
        row = addRow(form, gbc, row, "Model:", modelField, false);

        keyboardShortcutField = new JTextField();
        keyboardShortcutField.setToolTipText("e.g. ctrl+shift+r");
        row = addRow(form, gbc, row, "Keyboard Shortcut:", keyboardShortcutField, false);

        welcomeMessageArea = new JTextArea(2, 20);
        welcomeMessageArea.setLineWrap(true);
        welcomeMessageArea.setWrapStyleWord(true);
        row = addRow(form, gbc, row, "Welcome Message:", new JScrollPane(welcomeMessageArea), false);

        toolsField = new JTextField();
        toolsField.setToolTipText("Comma-separated tool names; supports @server, @server/tool, *, @builtin");
        row = addRow(form, gbc, row, "Tools:", toolsField, false);

        allowedToolsField = new JTextField();
        allowedToolsField.setToolTipText("Comma-separated tool names usable without prompting");
        row = addRow(form, gbc, row, "Allowed Tools:", allowedToolsField, false);

        includeMcpJsonCheckBox = new JCheckBox("Include MCP servers from mcp.json");
        addRow(form, gbc, row, "", includeMcpJsonCheckBox, false);

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
                    rawJsonArea.setText(RAW_MAPPER.writer(RAW_PRINTER).writeValueAsString(buildAgent()));
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
            "That doesn't parse as a valid agent object:\n\n" + error, "Invalid JSON", JOptionPane.ERROR_MESSAGE);
        return false;
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

    private static String joinCsv(List<String> values) {
        return values == null ? "" : String.join(", ", values);
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

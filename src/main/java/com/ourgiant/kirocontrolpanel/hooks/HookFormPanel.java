package com.ourgiant.kirocontrolpanel.hooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * The Form/Raw JSON tabs for editing one hook — everything
 * {@link HookEditDialog} needs except the dialog chrome (title, Save/Cancel,
 * config-level validation). Split out from the dialog specifically so this
 * can be unit tested: JDialog can't be constructed in a headless
 * environment, but a plain JPanel can.
 */
public class HookFormPanel extends JPanel {
    private static final String[] TRIGGERS = {
        "prompt_submit", "agent_stop", "pre_tool_use", "post_tool_use",
        "file_create", "file_save", "file_delete",
        "pre_task_execution", "post_task_execution", "manual_trigger"
    };
    private static final String ACTION_COMMAND = "Shell Command";
    private static final String ACTION_AGENT = "Agent Prompt";
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int FORM_TAB = 0;
    private static final int RAW_JSON_TAB = 1;

    private static final ObjectMapper RAW_MAPPER = JsonMapperFactory.createMapper();
    private static final PrettyPrinter RAW_PRINTER = JsonMapperFactory.createPrettyPrinter();

    private JTabbedPane tabbedPane;
    private JTextArea rawJsonArea;
    private int lastSelectedTabIndex = FORM_TAB;
    private boolean handlingTabChange = false;

    private JTextField nameField;
    private JComboBox<String> triggerCombo;
    private JTextField matcherField;
    private JCheckBox enabledCheckBox;
    private JSpinner timeoutSpinner;
    private JComboBox<String> actionTypeCombo;
    private JTextField commandField;
    private JTextArea promptArea;

    public HookFormPanel() {
        super(new BorderLayout());
        initializeUI();
    }

    public String getHookName() {
        return nameField.getText().trim();
    }

    public boolean isCommandType() {
        return ACTION_COMMAND.equals(actionTypeCombo.getSelectedItem());
    }

    public String getCommandText() {
        return commandField.getText().trim();
    }

    public String getPromptText() {
        return promptArea.getText().trim();
    }

    /** If the Raw JSON tab is active, parses it into the form first. @return false (and shows an error) if it doesn't parse. */
    public boolean syncFromActiveTabIfNeeded() {
        return tabbedPane.getSelectedIndex() != RAW_JSON_TAB || syncRawToFormShowingErrors();
    }

    public void populateFrom(Hook source) {
        nameField.setText(source.getName() == null ? "" : source.getName());
        triggerCombo.setSelectedItem(source.getTrigger() == null ? TRIGGERS[0] : source.getTrigger());
        matcherField.setText(source.getMatcher() == null ? "" : source.getMatcher());
        enabledCheckBox.setSelected(source.isEnabled());
        timeoutSpinner.setValue(source.getTimeout() == null ? DEFAULT_TIMEOUT_SECONDS : source.getTimeout());

        HookAction action = source.getAction();
        boolean isAgent = action != null && HookAction.TYPE_AGENT.equals(action.getType());
        actionTypeCombo.setSelectedItem(isAgent ? ACTION_AGENT : ACTION_COMMAND);
        commandField.setText(action != null && action.getCommand() != null ? action.getCommand() : "");
        promptArea.setText(action != null && action.getPrompt() != null ? action.getPrompt() : "");
        updateFieldAvailability();
    }

    /** Builds a hook object from the Form tab's current fields, without validating required ones. */
    public Hook buildHook() {
        Hook built = new Hook();
        built.setName(getHookName());
        built.setTrigger((String) triggerCombo.getSelectedItem());
        String matcher = matcherField.getText().trim();
        built.setMatcher(matcher.isEmpty() ? null : matcher);
        built.setEnabled(enabledCheckBox.isSelected());
        built.setTimeout((Integer) timeoutSpinner.getValue());

        HookAction action = new HookAction();
        if (isCommandType()) {
            action.setType(HookAction.TYPE_COMMAND);
            String command = getCommandText();
            action.setCommand(command.isEmpty() ? null : command);
        } else {
            action.setType(HookAction.TYPE_AGENT);
            String prompt = getPromptText();
            action.setPrompt(prompt.isEmpty() ? null : prompt);
        }
        built.setAction(action);
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
            populateFrom(RAW_MAPPER.readValue(rawJsonArea.getText(), Hook.class));
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

        nameField = new JTextField();
        row = addRow(form, gbc, row, "Name:", nameField, false);

        triggerCombo = new JComboBox<>(TRIGGERS);
        triggerCombo.setToolTipText("IDE event that fires this hook");
        row = addRow(form, gbc, row, "Trigger:", triggerCombo, false);

        matcherField = new JTextField();
        matcherField.setToolTipText(
            "Tool name (pre/post_tool_use) or file glob pattern (file_create/save/delete); ignored by other triggers");
        row = addRow(form, gbc, row, "Matcher:", matcherField, false);

        enabledCheckBox = new JCheckBox("Enabled");
        row = addRow(form, gbc, row, "", enabledCheckBox, false);

        timeoutSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_TIMEOUT_SECONDS, 0, 3600, 1));
        timeoutSpinner.setToolTipText("Seconds before the hook is cancelled; 0 disables the timeout");
        row = addRow(form, gbc, row, "Timeout (s):", timeoutSpinner, false);

        actionTypeCombo = new JComboBox<>(new String[] {ACTION_COMMAND, ACTION_AGENT});
        actionTypeCombo.addActionListener(e -> updateFieldAvailability());
        row = addRow(form, gbc, row, "Action Type:", actionTypeCombo, false);

        commandField = new JTextField();
        commandField.setToolTipText("Shell command to run; stdout is added to the agent's context on success");
        row = addRow(form, gbc, row, "Command:", commandField, false);

        promptArea = new JTextArea(4, 20);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        addRow(form, gbc, row, "Prompt:", new JScrollPane(promptArea), true);

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
                    rawJsonArea.setText(RAW_MAPPER.writer(RAW_PRINTER).writeValueAsString(buildHook()));
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
            "That doesn't parse as a valid hook object:\n\n" + error, "Invalid JSON", JOptionPane.ERROR_MESSAGE);
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

    private void updateFieldAvailability() {
        boolean isCommand = isCommandType();
        commandField.setEnabled(isCommand);
        promptArea.setEnabled(!isCommand);
    }
}

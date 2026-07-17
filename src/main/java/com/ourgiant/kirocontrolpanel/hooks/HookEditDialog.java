package com.ourgiant.kirocontrolpanel.hooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * Dialog for adding or editing one hook. Only mutates the in-memory
 * {@link Hook} passed in; the caller persists its containing file after
 * {@link #isSaved()}. Has a Form tab and a Raw JSON tab (the whole hook
 * object, including name/trigger/matcher/action/timeout/enabled) for
 * anything the form doesn't cover — the two stay in sync whenever you
 * switch tabs.
 */
public class HookEditDialog extends JDialog {
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

    private final Hook hook;

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

    private boolean saved = false;

    public HookEditDialog(Frame parent, Hook hook, boolean isNew) {
        super(parent, isNew ? "Add Hook" : "Edit Hook: " + hook.getName(), true);
        this.hook = hook;

        initializeUI();
        populateFormFrom(hook);
        updateFieldAvailability();
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

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Form", createFormTab());
        tabbedPane.addTab("Raw JSON", createRawJsonTab());
        tabbedPane.addChangeListener(e -> onTabChanged());
        add(tabbedPane, BorderLayout.CENTER);

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
                    rawJsonArea.setText(RAW_MAPPER.writer(RAW_PRINTER).writeValueAsString(buildHookFromForm()));
                    rawJsonArea.setCaretPosition(0);
                } catch (JsonProcessingException ignored) {
                    // Form fields always produce valid JSON; nothing to recover from.
                }
                lastSelectedTabIndex = newIndex;
            } else if (lastSelectedTabIndex == RAW_JSON_TAB && newIndex == FORM_TAB) {
                if (syncRawToForm()) {
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

    /** @return false (and shows an error) if the raw text doesn't parse as a hook object */
    private boolean syncRawToForm() {
        try {
            populateFormFrom(RAW_MAPPER.readValue(rawJsonArea.getText(), Hook.class));
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "That doesn't parse as a valid hook object:\n\n" + ex.getMessage(),
                "Invalid JSON", JOptionPane.ERROR_MESSAGE);
            return false;
        }
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
        boolean isCommand = ACTION_COMMAND.equals(actionTypeCombo.getSelectedItem());
        commandField.setEnabled(isCommand);
        promptArea.setEnabled(!isCommand);
    }

    private void populateFormFrom(Hook source) {
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
    private Hook buildHookFromForm() {
        Hook built = new Hook();
        built.setName(nameField.getText().trim());
        built.setTrigger((String) triggerCombo.getSelectedItem());
        String matcher = matcherField.getText().trim();
        built.setMatcher(matcher.isEmpty() ? null : matcher);
        built.setEnabled(enabledCheckBox.isSelected());
        built.setTimeout((Integer) timeoutSpinner.getValue());

        boolean isCommand = ACTION_COMMAND.equals(actionTypeCombo.getSelectedItem());
        HookAction action = new HookAction();
        if (isCommand) {
            action.setType(HookAction.TYPE_COMMAND);
            String command = commandField.getText().trim();
            action.setCommand(command.isEmpty() ? null : command);
        } else {
            action.setType(HookAction.TYPE_AGENT);
            String prompt = promptArea.getText().trim();
            action.setPrompt(prompt.isEmpty() ? null : prompt);
        }
        built.setAction(action);
        return built;
    }

    private void onSave() {
        if (tabbedPane.getSelectedIndex() == RAW_JSON_TAB && !syncRawToForm()) {
            return;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Hook name is required.", "Missing Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean isCommand = ACTION_COMMAND.equals(actionTypeCombo.getSelectedItem());
        if (isCommand && commandField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Command is required for a shell command action.", "Missing Command", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!isCommand && promptArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Prompt is required for an agent prompt action.", "Missing Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Hook built = buildHookFromForm();
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

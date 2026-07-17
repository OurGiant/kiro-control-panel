package com.ourgiant.kirocontrolpanel.hooks;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Read-only table view over a workspace's flattened hook list. */
class HookTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Name", "Trigger", "Matcher", "Status", "Action"};
    private static final int SUMMARY_MAX_LENGTH = 60;

    private List<HookEntry> entries = new ArrayList<>();

    void setEntries(List<HookEntry> entries) {
        this.entries = entries;
        fireTableDataChanged();
    }

    HookEntry entryAt(int row) {
        return entries.get(row);
    }

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Hook hook = entries.get(rowIndex).getHook();
        return switch (columnIndex) {
            case 0 -> hook.getName();
            case 1 -> hook.getTrigger();
            case 2 -> hook.getMatcher() == null ? "" : hook.getMatcher();
            case 3 -> hook.isEnabled() ? "Enabled" : "Disabled";
            case 4 -> summarize(hook.getAction());
            default -> "";
        };
    }

    private static String summarize(HookAction action) {
        if (action == null || action.getType() == null) {
            return "";
        }
        if (HookAction.TYPE_COMMAND.equals(action.getType())) {
            return "command: " + (action.getCommand() == null ? "" : action.getCommand());
        }
        if (HookAction.TYPE_AGENT.equals(action.getType())) {
            String prompt = action.getPrompt() == null ? "" : action.getPrompt();
            String truncated = prompt.length() > SUMMARY_MAX_LENGTH ? prompt.substring(0, SUMMARY_MAX_LENGTH) + "…" : prompt;
            return "agent: " + truncated;
        }
        return action.getType();
    }
}

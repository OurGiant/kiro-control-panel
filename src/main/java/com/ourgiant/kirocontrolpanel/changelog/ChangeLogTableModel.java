package com.ourgiant.kirocontrolpanel.changelog;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Read-only table view over a list of {@link ChangeLogEntry}, newest first (caller sorts). */
class ChangeLogTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Timestamp", "Scope", "File", "Surface", "Change", "Source"};
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private List<ChangeLogEntry> entries = new ArrayList<>();

    void setEntries(List<ChangeLogEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
        fireTableDataChanged();
    }

    ChangeLogEntry entryAt(int row) {
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
        ChangeLogEntry entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> TIMESTAMP_FORMAT.format(entry.timestamp());
            case 1 -> entry.scopeLabel();
            case 2 -> entry.path().getFileName().toString();
            case 3 -> entry.surface();
            case 4 -> entry.kind();
            case 5 -> entry.source();
            default -> "";
        };
    }
}

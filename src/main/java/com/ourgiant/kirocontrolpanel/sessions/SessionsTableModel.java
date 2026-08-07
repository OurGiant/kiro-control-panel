package com.ourgiant.kirocontrolpanel.sessions;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Read-only table view over a list of {@link SessionManifest}, newest first (caller sorts). */
class SessionsTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Date", "CWD", "Opening Prompt", "Files Touched"};
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int PROMPT_SNIPPET_MAX_LENGTH = 80;

    private List<SessionManifest> sessions = new ArrayList<>();

    void setSessions(List<SessionManifest> sessions) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
        fireTableDataChanged();
    }

    SessionManifest sessionAt(int row) {
        return sessions.get(row);
    }

    @Override
    public int getRowCount() {
        return sessions.size();
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
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 3 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SessionManifest manifest = sessions.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> TIMESTAMP_FORMAT.format(manifest.createdAt());
            case 1 -> manifest.cwd();
            case 2 -> snippet(manifest.title());
            case 3 -> manifest.filesTouchedCount();
            default -> "";
        };
    }

    static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "(no prompt)";
        }
        String singleLine = text.replace('\n', ' ').trim();
        return singleLine.length() > PROMPT_SNIPPET_MAX_LENGTH
            ? singleLine.substring(0, PROMPT_SNIPPET_MAX_LENGTH) + "..."
            : singleLine;
    }
}

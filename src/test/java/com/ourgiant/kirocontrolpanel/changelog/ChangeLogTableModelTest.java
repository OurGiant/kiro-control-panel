package com.ourgiant.kirocontrolpanel.changelog;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeLogTableModelTest {

    @Test
    void emptyModelHasNoRows() {
        ChangeLogTableModel model = new ChangeLogTableModel();

        assertEquals(0, model.getRowCount());
        assertEquals(6, model.getColumnCount());
    }

    @Test
    void setEntriesPopulatesRowsAndColumns() {
        ChangeLogTableModel model = new ChangeLogTableModel();
        ChangeLogEntry entry = new ChangeLogEntry(
            Instant.now().toEpochMilli(), "MCP", "Global", true,
            "/home/x/.kiro/settings/mcp.json", "CREATED", "SELF");

        model.setEntries(List.of(entry));

        assertEquals(1, model.getRowCount());
        assertEquals("Global", model.getValueAt(0, 1));
        assertEquals("mcp.json", model.getValueAt(0, 2));
        assertEquals("MCP", model.getValueAt(0, 3));
        assertEquals("CREATED", model.getValueAt(0, 4));
        assertEquals("SELF", model.getValueAt(0, 5));
        assertEquals(entry, model.entryAt(0));
    }
}

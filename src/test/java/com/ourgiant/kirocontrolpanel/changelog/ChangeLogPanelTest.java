package com.ourgiant.kirocontrolpanel.changelog;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLogPanelTest {

    private ChangeLogEntry someEntry() {
        return new ChangeLogEntry(Instant.now().toEpochMilli(), "MCP", "Global", true,
            "/home/x/.kiro/settings/mcp.json", "CREATED", "SELF", 0L);
    }

    @Test
    void emptyEntriesDisablesOpenFileButton() {
        ChangeLogPanel panel = new ChangeLogPanel();

        panel.setEntries(List.of());

        assertFalse(panel.getOpenFileButton().isEnabled());
    }

    @Test
    void selectingARowEnablesOpenFileButton() {
        ChangeLogPanel panel = new ChangeLogPanel();
        panel.setEntries(List.of(someEntry()));

        panel.getTable().setRowSelectionInterval(0, 0);

        assertTrue(panel.getOpenFileButton().isEnabled());
    }

    @Test
    void defaultPresetIsLastOneWeek() {
        ChangeLogPanel panel = new ChangeLogPanel();

        assertEquals("Last 1 week", panel.getPresetCombo().getSelectedItem());
    }

    @Test
    void narrowerPresetsProduceMoreRecentCutoffs() {
        ChangeLogPanel panel = new ChangeLogPanel();

        panel.getPresetCombo().setSelectedItem("Last 1 day");
        Instant oneDay = panel.selectedCutoff();
        panel.getPresetCombo().setSelectedItem("Last 1 week");
        Instant oneWeek = panel.selectedCutoff();
        panel.getPresetCombo().setSelectedItem("Last 2 weeks");
        Instant twoWeeks = panel.selectedCutoff();
        panel.getPresetCombo().setSelectedItem("Last 1 month");
        Instant oneMonth = panel.selectedCutoff();
        panel.getPresetCombo().setSelectedItem("Last 3 months");
        Instant threeMonths = panel.selectedCutoff();

        assertTrue(oneDay.isAfter(oneWeek));
        assertTrue(oneWeek.isAfter(twoWeeks));
        assertTrue(twoWeeks.isAfter(oneMonth));
        assertTrue(oneMonth.isAfter(threeMonths));
    }
}

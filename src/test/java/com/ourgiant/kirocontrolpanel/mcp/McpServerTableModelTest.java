package com.ourgiant.kirocontrolpanel.mcp;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerTableModelTest {

    private static final int STATUS_COLUMN = 2;

    private McpConfigFile configWith(String name, boolean disabled) {
        McpConfigFile config = new McpConfigFile();
        McpServerConfig server = new McpServerConfig();
        server.setCommand("npx");
        server.setDisabled(disabled);
        config.getMcpServers().put(name, server);
        return config;
    }

    @Test
    void statusIsPlainEnabledWhenNoRegistryDataIsKnown() {
        McpServerTableModel model = new McpServerTableModel();
        model.setConfig(configWith("atlassian", false));

        assertEquals("Enabled", model.getValueAt(0, STATUS_COLUMN));
    }

    @Test
    void flagsAnEnabledServerTheRegistryIgnores() {
        McpServerTableModel model = new McpServerTableModel();
        model.setConfig(configWith("awspricing", false));
        model.setIgnoredServerNames(Set.of("awspricing"));

        assertEquals("Enabled (blocked by org registry)", model.getValueAt(0, STATUS_COLUMN));
    }

    @Test
    void aLocallyDisabledServerStaysPlainDisabledRegardlessOfRegistryStatus() {
        McpServerTableModel model = new McpServerTableModel();
        model.setConfig(configWith("awspricing", true));
        model.setIgnoredServerNames(Set.of("awspricing"));

        assertEquals("Disabled", model.getValueAt(0, STATUS_COLUMN));
    }

    @Test
    void anEnabledServerNotInTheIgnoredSetIsUnaffected() {
        McpServerTableModel model = new McpServerTableModel();
        model.setConfig(configWith("atlassian", false));
        model.setIgnoredServerNames(Set.of("awspricing"));

        assertEquals("Enabled", model.getValueAt(0, STATUS_COLUMN));
    }
}

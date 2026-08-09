package com.ourgiant.kirocontrolpanel.mcp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Read-only table view over the current scope's {@link McpConfigFile}. Rows
 * are keyed by server name in insertion order (LinkedHashMap-backed), so the
 * file's own ordering is preserved.
 */
class McpServerTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Name", "Type", "Status", "Command / URL"};

    private McpConfigFile config = new McpConfigFile();
    private List<String> names = new ArrayList<>();
    // Server names a reachable-but-partially-approving org MCP registry ignores (issue #139).
    // Set independently of setConfig() so it survives scope reloads until the next background
    // registry check -- it reflects the whole account, not any one scope's config file.
    private Set<String> ignoredServerNames = Set.of();

    void setConfig(McpConfigFile config) {
        this.config = config != null ? config : new McpConfigFile();
        this.names = new ArrayList<>(this.config.getMcpServers().keySet());
        fireTableDataChanged();
    }

    void setIgnoredServerNames(Set<String> ignoredServerNames) {
        this.ignoredServerNames = ignoredServerNames != null ? ignoredServerNames : Set.of();
        fireTableDataChanged();
    }

    McpConfigFile getConfig() {
        return config;
    }

    String nameAt(int row) {
        return names.get(row);
    }

    McpServerConfig configAt(int row) {
        return config.getMcpServers().get(names.get(row));
    }

    @Override
    public int getRowCount() {
        return names.size();
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
        McpServerConfig server = configAt(rowIndex);
        return switch (columnIndex) {
            case 0 -> names.get(rowIndex);
            case 1 -> server.isRemote() ? "Remote" : "Local";
            case 2 -> statusText(server, names.get(rowIndex));
            case 3 -> summarize(server);
            default -> "";
        };
    }

    /**
     * A server the org registry ignores never actually runs even though it's enabled locally
     * (issue #139) -- worth flagging here since a disabled server's registry status is moot, it
     * wasn't going to run either way.
     */
    private String statusText(McpServerConfig server, String name) {
        if (server.isDisabled()) {
            return "Disabled";
        }
        return ignoredServerNames.contains(name) ? "Enabled (blocked by org registry)" : "Enabled";
    }

    private static String summarize(McpServerConfig server) {
        if (server.isRemote()) {
            return server.getUrl();
        }
        String command = server.getCommand() == null ? "" : server.getCommand();
        List<String> args = server.getArgs();
        if (args == null || args.isEmpty()) {
            return command;
        }
        return command + " " + String.join(" ", args);
    }
}

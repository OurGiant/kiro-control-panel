package com.ourgiant.kirocontrolpanel.agents;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import com.ourgiant.kirocontrolpanel.util.TextFilter;
import com.ourgiant.kirocontrolpanel.util.WorkspaceScopeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Agents tab: lists .kiro/agents/*.json for a Global or pinned-workspace
 * scope and lets the user create/edit/delete them -- one file per agent,
 * unlike MCP servers/hooks which are a map/array inside one file.
 */
public class AgentsPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(AgentsPanel.class);
    private static final Pattern VALID_AGENT_FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final AgentService agentService = new AgentService();
    private final DirectoryWatcher watcher;
    private final WorkspaceScopeBar scopeBar;

    private final DefaultListModel<AgentConfig> agentListModel = new DefaultListModel<>();
    private final JList<AgentConfig> agentList = new JList<>(agentListModel);
    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::applyFilter);
    private List<AgentConfig> allAgents = List.of();

    private JButton editButton;
    private JButton duplicateButton;
    private JButton deleteButton;
    private JButton revealButton;

    public AgentsPanel(AppPreferences preferences, DirectoryWatcher watcher) {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.watcher = watcher;
        watcher.addListener(this::reloadAgentList);

        scopeBar = new WorkspaceScopeBar(preferences);
        scopeBar.addScopeChangeListener(scope -> reloadAgentList());
        add(scopeBar, BorderLayout.NORTH);

        agentList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AgentConfig agent) {
                    setText(agent.getFileName());
                }
                return c;
            }
        });
        agentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonState();
            }
        });
        agentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && agentList.getSelectedValue() != null) {
                    onEdit();
                }
            }
        });
        add(SwingLayoutUtils.createFilterableContent(filterField, new JScrollPane(agentList)), BorderLayout.CENTER);
        add(createSideButtons(), BorderLayout.EAST);

        reloadAgentList();
    }

    private JPanel createSideButtons() {
        JButton newButton = new JButton("New...");
        newButton.setMnemonic(KeyEvent.VK_N);
        newButton.addActionListener(e -> onNew());

        editButton = new JButton("Edit...");
        editButton.setMnemonic(KeyEvent.VK_E);
        editButton.addActionListener(e -> onEdit());

        duplicateButton = new JButton("Duplicate...");
        duplicateButton.addActionListener(e -> onDuplicate());

        deleteButton = new JButton("Delete");
        deleteButton.setMnemonic(KeyEvent.VK_D);
        deleteButton.addActionListener(e -> onDelete());

        revealButton = new JButton("Reveal File...");
        revealButton.addActionListener(e -> onReveal());

        return SwingLayoutUtils.createVerticalButtonPanel(newButton, editButton, duplicateButton, deleteButton, revealButton);
    }

    private void reloadAgentList() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope != null) {
            allAgents = scope.isGlobal()
                ? agentService.listGlobal()
                : agentService.listWorkspace(scope.workspaceRoot());
            watcher.watch(scope.isGlobal()
                ? KiroPaths.globalAgentsDir()
                : KiroPaths.workspaceAgentsDir(scope.workspaceRoot()));
        } else {
            allAgents = List.of();
        }
        applyFilter();
    }

    private void applyFilter() {
        agentListModel.clear();
        String query = filterField.getText();
        for (AgentConfig agent : allAgents) {
            if (TextFilter.matches(agent.getFileName(), query)) {
                agentListModel.addElement(agent);
            }
        }
        updateButtonState();
    }

    private void updateButtonState() {
        boolean hasSelection = agentList.getSelectedValue() != null;
        editButton.setEnabled(hasSelection);
        duplicateButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
        revealButton.setEnabled(hasSelection);
    }

    private void onNew() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        if (scope == null) {
            return;
        }
        String fileName = JOptionPane.showInputDialog(this,
            "Agent file name (lowercase, hyphenated, e.g. aws-rust-agent):", "New Agent", JOptionPane.PLAIN_MESSAGE);
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        fileName = fileName.trim();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        if (!VALID_AGENT_FILE_NAME.matcher(fileName).matches()) {
            JOptionPane.showMessageDialog(this,
                "Agent file names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        fileName = fileName + ".json";

        Path dir = scope.isGlobal()
            ? KiroPaths.globalAgentsDir()
            : KiroPaths.workspaceAgentsDir(scope.workspaceRoot());
        Path filePath = dir.resolve(fileName);
        if (Files.exists(filePath)) {
            JOptionPane.showMessageDialog(this,
                "An agent named \"" + fileName + "\" already exists.", "Duplicate Agent", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AgentConfig agent = new AgentConfig(filePath, scope.workspaceRoot());
        AgentEditDialog dialog =
            new AgentEditDialog((Frame) SwingUtilities.getWindowAncestor(this), agent, true);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            persistThenReload(agent);
        }
    }

    private void onEdit() {
        AgentConfig selected = agentList.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            AgentConfig fresh = agentService.load(selected.getFilePath(), selected.getWorkspaceRoot());
            AgentEditDialog dialog =
                new AgentEditDialog((Frame) SwingUtilities.getWindowAncestor(this), fresh, false);
            dialog.setVisible(true);
            if (dialog.isSaved()) {
                persistThenReload(fresh);
            }
        } catch (IOException ex) {
            logger.error("Failed to load agent config: {}", selected.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to open: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDuplicate() {
        WorkspaceScope scope = scopeBar.getSelectedScope();
        AgentConfig selected = agentList.getSelectedValue();
        if (scope == null || selected == null) {
            return;
        }
        String currentName = selected.getFileName();
        String baseName = currentName.endsWith(".json")
            ? currentName.substring(0, currentName.length() - ".json".length())
            : currentName;
        Path dir = scope.isGlobal() ? KiroPaths.globalAgentsDir() : KiroPaths.workspaceAgentsDir(scope.workspaceRoot());
        String suggestedName = SwingLayoutUtils.suggestCopyName(baseName,
            candidate -> Files.exists(dir.resolve(candidate + ".json")));

        String fileName = (String) JOptionPane.showInputDialog(this,
            "Agent file name (lowercase, hyphenated, e.g. aws-rust-agent):", "Duplicate Agent",
            JOptionPane.PLAIN_MESSAGE, null, null, suggestedName);
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        fileName = fileName.trim();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        if (!VALID_AGENT_FILE_NAME.matcher(fileName).matches()) {
            JOptionPane.showMessageDialog(this,
                "Agent file names should be lowercase letters, digits, and hyphens only.",
                "Invalid Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        fileName = fileName + ".json";
        Path filePath = dir.resolve(fileName);
        if (Files.exists(filePath)) {
            JOptionPane.showMessageDialog(this,
                "An agent named \"" + fileName + "\" already exists.", "Duplicate Agent", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            AgentConfig fresh = agentService.load(selected.getFilePath(), selected.getWorkspaceRoot());
            AgentConfig copy = agentService.copy(fresh, filePath, scope.workspaceRoot());
            AgentEditDialog dialog =
                new AgentEditDialog((Frame) SwingUtilities.getWindowAncestor(this), copy, true);
            dialog.setVisible(true);
            if (dialog.isSaved()) {
                persistThenReload(copy);
            }
        } catch (IOException ex) {
            logger.error("Failed to duplicate agent config: {}", selected.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to duplicate: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void persistThenReload(AgentConfig agent) {
        try {
            agentService.save(agent);
            reloadAgentList();
        } catch (IOException ex) {
            logger.error("Failed to save agent config: {}", agent.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to save: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDelete() {
        AgentConfig selected = agentList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!SwingLayoutUtils.confirmDelete(this, "Delete Agent",
                "Delete \"" + selected.getFileName() + "\"? This cannot be undone.")) {
            return;
        }
        try {
            agentService.delete(selected);
            reloadAgentList();
        } catch (IOException ex) {
            logger.error("Failed to delete agent config: {}", selected.getFilePath(), ex);
            JOptionPane.showMessageDialog(this,
                "Failed to delete: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onReveal() {
        AgentConfig selected = agentList.getSelectedValue();
        if (selected != null) {
            DesktopUtils.revealInFileManager(this, selected.getFilePath());
        }
    }
}

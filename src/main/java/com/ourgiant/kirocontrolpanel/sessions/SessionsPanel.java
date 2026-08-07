package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.InAppFileEditorLauncher;
import com.ourgiant.kirocontrolpanel.util.SwingLayoutUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain-JPanel content for the Sessions tab -- added directly to
 * {@code MainWindow}'s {@code JTabbedPane} (not a Help-menu dialog like
 * {@code ChangeLogDialog}/{@code KiroSetupScanDialog}, so there's no thin
 * {@code *Dialog} wrapper here). No {@code WorkspaceScopeBar}: kiro-cli
 * sessions carry a {@code cwd} per session but aren't pinned-workspace
 * scoped the way MCP/Steering/etc. are -- one flat table across all
 * sessions, {@code cwd} as a plain filterable/displayed column, same shape
 * {@code ChangeLogPanel} already established for the same reason.
 * <p>
 * Owns no indexing I/O itself -- it's handed a {@link SessionIndexService}
 * and reloads from it, either on construction or whenever the service's
 * update listener fires (a background scan or the live watcher completing).
 */
public class SessionsPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(SessionsPanel.class);
    private static final int SEARCH_RESULT_LIMIT = 200;
    private static final DateTimeFormatter SUMMARY_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SessionIndexService indexService;

    private final JTextField filterField = SwingLayoutUtils.createFilterField(this::onFilterChanged);
    private final SessionsTableModel tableModel = new SessionsTableModel();
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Indexing…");

    private final JTextArea summaryArea = new JTextArea();
    private final DefaultListModel<String> filesListModel = new DefaultListModel<>();
    private final JList<String> filesList = new JList<>(filesListModel);
    private final JButton viewRawJsonButton = new JButton("View Raw JSON...");
    private final JButton revealFileButton = new JButton("Reveal File");
    private final JButton revealSessionButton = new JButton("Reveal Session Files");

    private List<SessionManifest> allSessions = new ArrayList<>();
    private SessionManifest selectedManifest;
    /** Non-null while the table is showing FTS results rather than the live filter, so a
     * background {@link #reload()} (a new session arriving) re-runs the same search instead of
     * silently falling back to a plain substring match against the leftover query text -- see
     * issue #117's end-to-end verification, which caught this reverting/emptying the results. */
    private String activeSearchQuery;

    public SessionsPanel(AppPreferences preferences, SessionIndexService indexService) {
        super(new BorderLayout(8, 8));
        this.indexService = indexService;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        filterField.addActionListener(e -> onSearch());

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailPane();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), buildDetailPanel());
        split.setResizeWeight(0.6);

        add(SwingLayoutUtils.createFilterableContent(filterField, split), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        indexService.setUpdateListener(() -> javax.swing.SwingUtilities.invokeLater(this::reload));
        reload();
        updateDetailPane();
    }

    private JPanel buildDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Session Detail"));

        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setRows(6);
        panel.add(new JScrollPane(summaryArea), BorderLayout.NORTH);

        filesList.setVisibleRowCount(6);
        filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                revealFileButton.setEnabled(filesList.getSelectedIndex() >= 0);
            }
        });
        panel.add(new JScrollPane(filesList), BorderLayout.CENTER);

        viewRawJsonButton.addActionListener(e -> onViewRawJson());
        revealFileButton.addActionListener(e -> onRevealFile());
        revealSessionButton.addActionListener(e -> onRevealSession());
        panel.add(SwingLayoutUtils.createVerticalButtonPanel(viewRawJsonButton, revealFileButton, revealSessionButton),
            BorderLayout.EAST);

        return panel;
    }

    /** Re-reads every session from the index and re-applies whatever filter/search is active. */
    void reload() {
        try {
            allSessions = indexService.listAll();
            statusLabel.setText(allSessions.size() + " session" + (allSessions.size() == 1 ? "" : "s") + " indexed");
        } catch (SQLException e) {
            logger.warn("Failed to load sessions from the index", e);
            statusLabel.setText("Failed to load sessions: " + e.getMessage());
            allSessions = new ArrayList<>();
        }
        if (activeSearchQuery != null) {
            runSearch(activeSearchQuery);
        } else {
            applyLiveFilter();
        }
    }

    private void onFilterChanged() {
        activeSearchQuery = null;
        applyLiveFilter();
    }

    /** Fast, in-memory substring match over already-loaded manifest fields -- fires on every
     * keystroke. Full-text search over transcript content is a separate, heavier action (Enter). */
    private void applyLiveFilter() {
        String needle = filterField.getText().trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            tableModel.setSessions(allSessions);
            return;
        }
        List<SessionManifest> filtered = allSessions.stream()
            .filter(m -> matchesFilter(m, needle))
            .toList();
        tableModel.setSessions(filtered);
    }

    private static boolean matchesFilter(SessionManifest manifest, String needle) {
        return containsIgnoreCase(manifest.cwd(), needle)
            || containsIgnoreCase(manifest.title(), needle)
            || containsIgnoreCase(manifest.sessionCreatedReason(), needle);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** Enter in the filter field: an FTS5 query against the full transcript index, narrowing
     * the table to ranked matches -- the issue's own preferred "Enter in the filter field"
     * interaction over a separate search box. */
    private void onSearch() {
        String query = filterField.getText().trim();
        if (query.isEmpty()) {
            activeSearchQuery = null;
            applyLiveFilter();
            return;
        }
        runSearch(query);
    }

    private void runSearch(String query) {
        try {
            List<SessionSearchResult> results = indexService.search(query, SEARCH_RESULT_LIMIT);
            Map<String, SessionManifest> byId = new LinkedHashMap<>();
            for (SessionManifest manifest : allSessions) {
                byId.put(manifest.sessionId(), manifest);
            }
            List<SessionManifest> matched = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (SessionSearchResult result : results) {
                if (seen.add(result.sessionId())) {
                    SessionManifest manifest = byId.get(result.sessionId());
                    if (manifest != null) {
                        matched.add(manifest);
                    }
                }
            }
            tableModel.setSessions(matched);
            statusLabel.setText(matched.size() + " search result" + (matched.size() == 1 ? "" : "s") + " for \"" + query + "\"");
            activeSearchQuery = query;
        } catch (SQLException e) {
            logger.warn("Session search failed for query '{}'", query, e);
            statusLabel.setText("Search failed: " + e.getMessage());
        }
    }

    private void updateDetailPane() {
        int row = table.getSelectedRow();
        selectedManifest = row >= 0 ? tableModel.sessionAt(row) : null;

        filesListModel.clear();
        if (selectedManifest == null) {
            summaryArea.setText("");
            viewRawJsonButton.setEnabled(false);
            revealFileButton.setEnabled(false);
            revealSessionButton.setEnabled(false);
            return;
        }

        summaryArea.setText(summaryText(selectedManifest));
        for (String file : selectedManifest.filesTouched()) {
            filesListModel.addElement(file);
        }
        viewRawJsonButton.setEnabled(true);
        revealFileButton.setEnabled(false);
        revealSessionButton.setEnabled(true);
    }

    private static String summaryText(SessionManifest manifest) {
        return "Session: " + manifest.sessionId() + "\n"
            + "Directory: " + manifest.cwd() + "\n"
            + "Started: " + SUMMARY_TIMESTAMP_FORMAT.format(manifest.createdAt()) + "\n"
            + "Last activity: " + SUMMARY_TIMESTAMP_FORMAT.format(manifest.updatedAt()) + "\n"
            + "Started via: " + manifest.sessionCreatedReason() + "\n"
            + "Messages: " + manifest.messageCount() + "\n"
            + "Prompt: " + (manifest.title() != null ? manifest.title() : "(none)");
    }

    private void onViewRawJson() {
        if (selectedManifest == null) {
            return;
        }
        new SessionRawViewerDialog(InAppFileEditorLauncher.resolveFrame(this), selectedManifest.sourceJson())
            .setVisible(true);
    }

    private void onRevealFile() {
        String selected = filesList.getSelectedValue();
        if (selected != null) {
            DesktopUtils.revealInFileManager(this, Paths.get(selected));
        }
    }

    private void onRevealSession() {
        if (selectedManifest != null) {
            Path target = selectedManifest.sourceJsonl();
            DesktopUtils.revealInFileManager(this, target);
        }
    }

    /** Package-private, for tests. */
    JTextField getFilterField() {
        return filterField;
    }

    /** Package-private, for tests. */
    JTable getTable() {
        return table;
    }

    /** Package-private, for tests. */
    JLabel getStatusLabel() {
        return statusLabel;
    }

    /** Package-private, for tests. */
    JButton getViewRawJsonButton() {
        return viewRawJsonButton;
    }

    /** Package-private, for tests. */
    JButton getRevealFileButton() {
        return revealFileButton;
    }

    /** Package-private, for tests. */
    JList<String> getFilesList() {
        return filesList;
    }
}

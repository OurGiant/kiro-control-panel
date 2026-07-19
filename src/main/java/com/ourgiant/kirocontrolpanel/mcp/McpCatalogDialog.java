package com.ourgiant.kirocontrolpanel.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Browse kiro.dev's known MCP servers (bundled via {@link McpCatalogService},
 * or freshly fetched on demand via the Refresh button) and pick one to add.
 * Non-modal like LogViewerDialog -- unlike McpServerEditDialog's single
 * blocking decision, browsing/filtering is a keep-it-open activity, and a
 * user may want to add several servers in one session. Only mutates the
 * in-memory {@link McpConfigFile} passed in (via the McpServerEditDialog it
 * opens); persistence is the caller's job, signaled through {@code onServerAdded}.
 * <p>
 * {@code configSupplier} is re-queried on every add rather than captured
 * once: the caller's persist-then-reload after each add typically replaces
 * its live {@code McpConfigFile} object wholesale (a fresh disk read), so a
 * second add in the same browsing session must not mutate the now-stale
 * object from before the first add's reload.
 */
public class McpCatalogDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(McpCatalogDialog.class);
    private static final String CATALOG_URL =
        "https://raw.githubusercontent.com/OurGiant/kiro-control-panel/main/src/main/resources/mcp-catalog.json";

    private final Frame parentFrame;
    private final Supplier<McpConfigFile> configSupplier;
    private final Runnable onServerAdded;
    private final McpCatalogService catalogService = new McpCatalogService();
    private List<McpCatalogEntry> allEntries;

    private final JTextField filterField = new JTextField();
    private final DefaultListModel<McpCatalogEntry> listModel = new DefaultListModel<>();
    private final JList<McpCatalogEntry> list = new JList<>(listModel);
    private final JButton addButton = new JButton("Add to Kiro...");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel statusLabel = new JLabel(" ");

    public McpCatalogDialog(Frame parent, Supplier<McpConfigFile> configSupplier, Runnable onServerAdded) {
        super(parent, "MCP Server Catalog", false);
        this.parentFrame = parent;
        this.configSupplier = configSupplier;
        this.onServerAdded = onServerAdded;
        this.allEntries = catalogService.load();

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createFilterField(), BorderLayout.NORTH);
        add(createList(), BorderLayout.CENTER);
        add(createSouthPanel(), BorderLayout.SOUTH);

        applyFilter("");
        setPreferredSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(parent);
    }

    private JComponent createFilterField() {
        filterField.getDocument().addDocumentListener((SimpleDocumentListener) e -> applyFilter(filterField.getText()));
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel("Filter:"), BorderLayout.WEST);
        panel.add(filterField, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new CatalogEntryRenderer());
        list.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                addButton.setEnabled(list.getSelectedValue() != null);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    onAddSelected();
                }
            }
        });
        return new JScrollPane(list);
    }

    private JComponent createSouthPanel() {
        addButton.setEnabled(false);
        addButton.addActionListener(e -> onAddSelected());
        refreshButton.addActionListener(e -> onRefresh());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(addButton);
        buttonPanel.add(closeButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(statusLabel, BorderLayout.WEST);
        southPanel.add(buttonPanel, BorderLayout.EAST);
        return southPanel;
    }

    private void onRefresh() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing catalog...");
        statusLabel.setForeground(Color.GRAY);

        SwingWorker<Optional<List<McpCatalogEntry>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<List<McpCatalogEntry>> doInBackground() {
                return catalogService.fetchFromUrl(CATALOG_URL);
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                Optional<List<McpCatalogEntry>> result;
                try {
                    result = get();
                } catch (Exception e) {
                    logger.warn("Catalog refresh failed", e);
                    statusLabel.setText("Could not refresh catalog");
                    statusLabel.setForeground(Color.GRAY);
                    return;
                }
                if (result.isEmpty()) {
                    statusLabel.setText("Could not refresh catalog");
                    statusLabel.setForeground(Color.GRAY);
                    return;
                }
                allEntries = result.get();
                applyFilter(filterField.getText());
                statusLabel.setText("Catalog updated");
                statusLabel.setForeground(new Color(0, 128, 0));
            }
        };
        worker.execute();
    }

    private void applyFilter(String query) {
        McpCatalogEntry previousSelection = list.getSelectedValue();
        listModel.clear();
        for (McpCatalogEntry entry : McpCatalogEntry.filter(allEntries, query)) {
            listModel.addElement(entry);
        }
        if (previousSelection != null) {
            list.setSelectedValue(previousSelection, true);
        }
    }

    private void onAddSelected() {
        McpCatalogEntry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        McpServerEditDialog dialog =
            new McpServerEditDialog(parentFrame, configSupplier.get(), entry.getName(), entry.getConfig());
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            onServerAdded.run();
        }
    }

    private static final class CatalogEntryRenderer extends JPanel implements ListCellRenderer<McpCatalogEntry> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel descriptionLabel = new JLabel();
        private final JLabel requirementLabel = new JLabel();

        CatalogEntryRenderer() {
            super();
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.PLAIN, descriptionLabel.getFont().getSize() - 1f));
            requirementLabel.setFont(requirementLabel.getFont().deriveFont(Font.ITALIC, requirementLabel.getFont().getSize() - 1f));
            add(nameLabel);
            add(descriptionLabel);
            add(requirementLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends McpCatalogEntry> list, McpCatalogEntry entry,
                                                        int index, boolean isSelected, boolean cellHasFocus) {
            boolean setupRequired = entry.getConfig() != null && entry.getConfig().isDisabled();
            nameLabel.setText(entry.getName() + (setupRequired ? "  [Setup required]" : ""));
            descriptionLabel.setText(entry.getDescription() == null ? "" : entry.getDescription());
            requirementLabel.setText(entry.getRequirement() == null ? " " : entry.getRequirement());

            Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(background);
            nameLabel.setForeground(setupRequired && !isSelected ? Color.ORANGE.darker() : foreground);
            descriptionLabel.setForeground(foreground);
            requirementLabel.setForeground(foreground);
            return this;
        }

        @Override
        public boolean isOpaque() {
            return true;
        }
    }

    @FunctionalInterface
    private interface SimpleDocumentListener extends DocumentListener {
        void onChange(DocumentEvent e);

        @Override
        default void insertUpdate(DocumentEvent e) {
            onChange(e);
        }

        @Override
        default void removeUpdate(DocumentEvent e) {
            onChange(e);
        }

        @Override
        default void changedUpdate(DocumentEvent e) {
            onChange(e);
        }
    }
}

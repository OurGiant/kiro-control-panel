package com.ourgiant.kirocontrolpanel.mcp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Supplier;

/**
 * Browse kiro.dev's known MCP servers (bundled via {@link McpCatalogService},
 * never live-fetched) and pick one to add. Non-modal like LogViewerDialog --
 * unlike McpServerEditDialog's single blocking decision, browsing/filtering
 * is a keep-it-open activity, and a user may want to add several servers in
 * one session. Only mutates the in-memory {@link McpConfigFile} passed in
 * (via the McpServerEditDialog it opens); persistence is the caller's job,
 * signaled through {@code onServerAdded}.
 * <p>
 * {@code configSupplier} is re-queried on every add rather than captured
 * once: the caller's persist-then-reload after each add typically replaces
 * its live {@code McpConfigFile} object wholesale (a fresh disk read), so a
 * second add in the same browsing session must not mutate the now-stale
 * object from before the first add's reload.
 */
public class McpCatalogDialog extends JDialog {

    private final Frame parentFrame;
    private final Supplier<McpConfigFile> configSupplier;
    private final Runnable onServerAdded;
    private final List<McpCatalogEntry> allEntries;

    private final JTextField filterField = new JTextField();
    private final DefaultListModel<McpCatalogEntry> listModel = new DefaultListModel<>();
    private final JList<McpCatalogEntry> list = new JList<>(listModel);
    private final JButton addButton = new JButton("Add to Kiro...");

    public McpCatalogDialog(Frame parent, Supplier<McpConfigFile> configSupplier, Runnable onServerAdded) {
        super(parent, "MCP Server Catalog", false);
        this.parentFrame = parent;
        this.configSupplier = configSupplier;
        this.onServerAdded = onServerAdded;
        this.allEntries = new McpCatalogService().load();

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(createFilterField(), BorderLayout.NORTH);
        add(createList(), BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

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

    private JComponent createButtonPanel() {
        addButton.setEnabled(false);
        addButton.addActionListener(e -> onAddSelected());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(addButton);
        panel.add(closeButton);
        return panel;
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

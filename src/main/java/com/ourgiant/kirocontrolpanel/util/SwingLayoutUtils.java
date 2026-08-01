package com.ourgiant.kirocontrolpanel.util;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/** Small layout helpers shared by the MCP/Steering/Skills/Hooks panels, which are otherwise near-identical. */
public final class SwingLayoutUtils {

    private SwingLayoutUtils() {
    }

    /** A vertical stack of buttons, left-aligned, full-width, with a trailing glue — the side-button-panel pattern every panel uses. */
    public static JPanel createVerticalButtonPanel(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JButton button : buttons) {
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
            panel.add(button);
            panel.add(Box.createVerticalStrut(6));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /** The "Delete X? This cannot be undone." confirm dialog every panel's delete/remove action uses. */
    public static boolean confirmDelete(Component parent, String title, String message) {
        int confirm = JOptionPane.showConfirmDialog(parent, message, title,
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return confirm == JOptionPane.YES_OPTION;
    }

    /** A text field that invokes {@code onChange} on every keystroke -- the live-filter-box pattern shared by every list/table panel. */
    public static JTextField createFilterField(Runnable onChange) {
        JTextField field = new JTextField();
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.run();
            }
        });
        return field;
    }

    /**
     * Stacks a filter field above the scrollable list/table content -- the shared
     * content-area shape for every list/table panel. Deliberately does NOT nest
     * {@code WorkspaceScopeBar} inside a new container: {@code WorkspaceScopeBar}'s
     * {@code WrapLayout} (issue #18) computes its wrapped preferred height from its
     * own current width, which only gets set correctly one BorderLayout level at a
     * time during a layout pass -- wrapping it in an extra container broke that and
     * reintroduced #18's clipping bug one level up. Wrapping the (non-width-dependent)
     * filter field and scroll content instead avoids that trap entirely.
     */
    public static JPanel createFilterableContent(JTextField filterField, JComponent scrollableContent) {
        JPanel filterRow = new JPanel(new BorderLayout(4, 0));
        filterRow.add(new JLabel("Filter:"), BorderLayout.WEST);
        filterRow.add(filterField, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.add(filterRow, BorderLayout.NORTH);
        content.add(scrollableContent, BorderLayout.CENTER);
        return content;
    }
}

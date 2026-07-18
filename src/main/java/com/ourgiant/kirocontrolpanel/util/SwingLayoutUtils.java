package com.ourgiant.kirocontrolpanel.util;

import javax.swing.*;
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
}

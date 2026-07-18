package com.ourgiant.kirocontrolpanel;

import javax.swing.*;
import java.awt.*;

/**
 * One-time welcome dialog explaining the app's core idea (it edits Kiro's
 * own files directly) and the tray-resident/workspace-pinning UX, since
 * neither is obvious from a blank tabbed window on first launch.
 */
public class FirstRunDialog extends JDialog {

    public FirstRunDialog(Frame parent) {
        super(parent, "Welcome to Kiro Control Panel", true);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JEditorPane note = new JEditorPane("text/html", buildHtml());
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);

        // JEditorPane's own getPreferredSize() doesn't account for how its HTML
        // reflows, so pack() under-measures the height and clips content; sizing
        // the wrapping JScrollPane instead is reliable, and doubles as a safety
        // net (scrolls instead of clipping) if the text ever grows.
        JScrollPane scrollPane = new JScrollPane(note);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(500, 360));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton dismissButton = new JButton("Got it, let's go");
        dismissButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(dismissButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(dismissButton);

        pack();
        setLocationRelativeTo(parent);
    }

    private static String buildHtml() {
        return """
            <html><body style="font-family: sans-serif;">
            <p>This app reads and writes the exact files Kiro itself uses
            (<code>~/.kiro/...</code> and <code>&lt;workspace&gt;/.kiro/...</code>).
            Changes made here take effect in Kiro immediately, and vice versa &mdash;
            nothing is duplicated or synced separately.</p>
            <p><b>Lives in the tray.</b> Closing this window just hides it; use
            File &gt; Quit (or the tray icon's Exit) to actually exit.</p>
            <p><b>Global vs. workspace.</b> Global-scope resources are always
            available. To manage a project's own MCP servers, steering docs,
            skills, or hooks, pin it via "Add Workspace..." in any panel's scope
            bar.</p>
            <p><b>Theming.</b> Config &gt; Theme to switch appearance.</p>
            </body></html>
            """;
    }
}

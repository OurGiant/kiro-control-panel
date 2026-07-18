package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.util.AppVersion;
import com.ourgiant.kirocontrolpanel.util.IconFactory;

import javax.swing.*;
import java.awt.*;

/** Simple About dialog — app name, version, and vendor, since nothing else in the running app shows a build identifier. */
public class AboutDialog extends JDialog {

    public AboutDialog(Frame parent) {
        super(parent, "About Kiro Control Panel", true);

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel iconLabel = new JLabel(new ImageIcon(IconFactory.createAppIcon(48)));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        add(iconLabel, BorderLayout.WEST);

        JEditorPane note = new JEditorPane("text/html", buildHtml());
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);
        JScrollPane scrollPane = new JScrollPane(note);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(360, 140));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        pack();
        setLocationRelativeTo(parent);
    }

    private static String buildHtml() {
        return """
            <html><body style="font-family: sans-serif;">
            <h2 style="margin-top: 0;">Kiro Control Panel</h2>
            <p>Version %s</p>
            <p>System-tray Swing app for managing Kiro's MCP servers,
            steering docs, skills, and hooks outside the IDE &mdash; edits
            the same files Kiro itself reads.</p>
            <p>&copy; OurGiant</p>
            </body></html>
            """.formatted(AppVersion.resolve());
    }
}

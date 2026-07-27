package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.util.AppVersion;
import com.ourgiant.kirocontrolpanel.util.IconFactory;
import com.ourgiant.kirocontrolpanel.util.NetworkFetchException;
import com.ourgiant.kirocontrolpanel.util.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Optional;

/** Simple About dialog — app name, version, and vendor, since nothing else in the running app shows a build identifier. Also checks GitHub for a newer release. */
public class AboutDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(AboutDialog.class);

    /** Help > About: does its own live check, same as always. */
    public AboutDialog(Frame parent) {
        this(parent, null);
    }

    /**
     * TrayApp's silent startup check (#68) already knows {@code knownNewerRelease} -- skips
     * a second, redundant network call and shows it immediately instead of flashing
     * "Checking for updates..." first. Pass {@code null} to check live instead, same as the
     * single-arg constructor.
     * <p>
     * Non-modal exactly when {@code knownNewerRelease} is non-null: the auto-shown case
     * (silent startup check found a newer version) must never block the main window --
     * the user can ignore it entirely, keep working, and stay on the current version if
     * they want. Help > About (a deliberate click) stays modal, the normal expectation
     * for that kind of dialog. See #76.
     */
    public AboutDialog(Frame parent, UpdateChecker.ReleaseInfo knownNewerRelease) {
        super(parent, "About Kiro Control Panel", knownNewerRelease == null);
        String currentVersion = AppVersion.resolve();

        setLayout(new BorderLayout(12, 12));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel iconLabel = new JLabel(new ImageIcon(IconFactory.createAppIcon(48)));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        add(iconLabel, BorderLayout.WEST);

        JEditorPane note = new JEditorPane("text/html", buildHtml(currentVersion));
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);
        JScrollPane scrollPane = new JScrollPane(note);
        scrollPane.setBorder(null);
        // Sized on the generous side deliberately -- JEditorPane's HTML preferred-size
        // calculation is unreliable for wrapping (see FirstRunDialog's sizing fix), so a
        // snug viewport tends to read as "too small"/borderline-scrolling rather than
        // actually being cut off.
        scrollPane.setPreferredSize(new Dimension(420, 300));
        add(scrollPane, BorderLayout.CENTER);

        JLabel updateLabel = new JLabel("Checking for updates...");
        updateLabel.setForeground(Color.GRAY);
        if (knownNewerRelease != null) {
            applyNewerReleaseAvailable(updateLabel, knownNewerRelease);
        } else {
            startUpdateCheck(updateLabel, currentVersion);
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(closeButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(updateLabel, BorderLayout.WEST);
        southPanel.add(buttonPanel, BorderLayout.EAST);
        add(southPanel, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(parent);
    }

    private static String buildHtml(String currentVersion) {
        return """
            <html><body style="font-family: sans-serif;">
            <h2 style="margin-top: 0;">Kiro Control Panel</h2>
            <p>Version %s</p>
            <p>System-tray Swing app for managing Kiro's MCP servers,
            steering docs, skills, and hooks outside the IDE &mdash; edits
            the same files Kiro itself reads.</p>
            <p>&copy; OurGiant</p>
            </body></html>
            """.formatted(currentVersion);
    }

    private void startUpdateCheck(JLabel updateLabel, String currentVersion) {
        SwingWorker<Optional<UpdateChecker.ReleaseInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<UpdateChecker.ReleaseInfo> doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                Optional<UpdateChecker.ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    logger.warn("Update check failed", e);
                    updateLabel.setText(e.getCause() instanceof NetworkFetchException nfe
                        ? nfe.getMessage() : "Could not check for updates");
                    return;
                }
                if (release.isEmpty()) {
                    updateLabel.setText("Could not check for updates");
                    return;
                }
                UpdateChecker.ReleaseInfo info = release.get();
                if (!UpdateChecker.isNewerVersion(info.version(), currentVersion)) {
                    updateLabel.setText("Up to date");
                    updateLabel.setForeground(new Color(0, 128, 0));
                    return;
                }
                applyNewerReleaseAvailable(updateLabel, info);
            }
        };
        worker.execute();
    }

    private void applyNewerReleaseAvailable(JLabel updateLabel, UpdateChecker.ReleaseInfo info) {
        updateLabel.setText("<html><a href=''>Version " + info.version() + " available</a></html>");
        updateLabel.setForeground(new Color(0, 102, 204));
        updateLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    URI uri = new URI(info.htmlUrl());
                    if (!isTrustedReleaseUrl(uri)) {
                        logger.warn("Refusing to open untrusted release URL: {}", info.htmlUrl());
                        return;
                    }
                    Desktop.getDesktop().browse(uri);
                } catch (Exception ex) {
                    logger.warn("Could not open release URL in browser", ex);
                }
            }
        });
    }

    /**
     * Defense in depth, not a response to a live exploit: {@code htmlUrl} comes straight from
     * GitHub's releases API response, so a tampered response (only possible with an existing
     * TLS MITM position) could otherwise point this at an arbitrary URI/scheme. Restrict to
     * exactly the host the API is expected to point back to. See #71.
     */
    static boolean isTrustedReleaseUrl(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost());
    }
}

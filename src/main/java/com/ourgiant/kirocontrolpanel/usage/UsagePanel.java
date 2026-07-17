package com.ourgiant.kirocontrolpanel.usage;

import javax.swing.*;
import java.awt.*;

/**
 * Usage tab. Held as a static note rather than a real feature: Kiro has no
 * scriptable API for personal credit/usage balance (kiro-cli's headless mode
 * explicitly excludes slash commands like /usage, and the web dashboard is
 * org-admin-only, not a personal-balance view). See SPEC.md for the full
 * writeup of what was tried and ruled out. Revisit once kirodotdev/Kiro#7752
 * ships a scriptable usage API.
 */
public class UsagePanel extends JPanel {

    public UsagePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JEditorPane note = new JEditorPane("text/html", buildHtml());
        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);

        add(note, BorderLayout.NORTH);
    }

    private static String buildHtml() {
        return """
            <html><body style="font-family: sans-serif;">
            <h2>Usage tracking isn't available here yet</h2>
            <p>Kiro doesn't currently expose a machine-readable API for your personal
            credit/usage balance &mdash; only an interactive slash command inside the
            CLI's chat REPL, and a web dashboard that's org-admin-only (it needs IAM
            Identity Center admin permissions most individual developers don't have).
            Neither fits a background tool like this one, so this panel stays a
            placeholder rather than something fragile or misleading.</p>
            <p><b>To check your usage right now:</b></p>
            <ol>
              <li>Run <code>kiro-cli</code></li>
              <li>Type <code>/usage</code></li>
            </ol>
            <p>If you already have the CLI open for other work &mdash; and most people
            using this tool will &mdash; that's the fastest path anyway. We'll revisit
            this panel once the Kiro team ships a scriptable usage API, tracked
            upstream at <code>kirodotdev/Kiro#7752</code>.</p>
            </body></html>
            """;
    }
}

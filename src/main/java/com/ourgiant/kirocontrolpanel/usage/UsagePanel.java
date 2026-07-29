package com.ourgiant.kirocontrolpanel.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Usage tab. Fetches the user's live credit/plan balance by driving the
 * local {@code kiro-cli} binary over its own ACP interface (see
 * {@link KiroUsageService}) -- issue #94, following up on the static
 * placeholder this tab shipped as (SPEC.md's Usage non-goal, upstream
 * {@code kirodotdev/Kiro#7752}). Falls back to the original static note,
 * unchanged, if {@code kiro-cli} isn't installed or the call fails for any
 * reason -- this must never hang or crash the panel, since the underlying
 * ACP method is an undocumented CLI internal that could change shape at any
 * time.
 */
public class UsagePanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(UsagePanel.class);

    private final JEditorPane note = new JEditorPane("text/html", "");
    private final JButton refreshButton = new JButton("Refresh");
    private final JLabel statusLabel = new JLabel(" ");

    public UsagePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        note.setEditable(false);
        note.setOpaque(false);
        note.setBorder(null);

        // Scrollable, not BorderLayout.NORTH directly: NORTH sizes its child to
        // the child's own reported preferredSize().height, which for a JEditorPane
        // rendering HTML depends on the width it reflows at -- at a narrow window
        // width the note can need more vertical space than was allocated, with no
        // way to see the rest if it's clipped outright (issue #22 caught this).
        JScrollPane scrollPane = new JScrollPane(note);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> refresh());
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(statusLabel, BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        add(headerPanel, BorderLayout.NORTH);

        refresh();
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Checking kiro-cli for live usage…");
        note.setText(buildCheckingHtml());

        SwingWorker<KiroUsageService.UsageSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected KiroUsageService.UsageSnapshot doInBackground() {
                return KiroUsageService.fetchUsage();
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                statusLabel.setText(" ");
                try {
                    note.setText(buildUsageHtml(get()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String reason = cause instanceof KiroUsageService.UsageFetchException
                        ? cause.getMessage() : "Unexpected error talking to kiro-cli";
                    logger.warn("Live usage fetch failed, falling back to static note: {}", reason, cause);
                    note.setText(buildFallbackHtml(reason));
                }
                note.setCaretPosition(0);
            }
        };
        worker.execute();
    }

    private static String buildCheckingHtml() {
        return """
            <html><body style="font-family: sans-serif;">
            <h2>Checking kiro-cli for your usage…</h2>
            <p>Asking the local <code>kiro-cli</code> for your current plan/credit balance.</p>
            </body></html>
            """;
    }

    private static String buildUsageHtml(KiroUsageService.UsageSnapshot snapshot) {
        StringBuilder rows = new StringBuilder();
        for (KiroUsageService.UsageBreakdown b : snapshot.breakdowns()) {
            rows.append("<tr>")
                .append("<td>").append(escapeHtml(b.displayName())).append("</td>")
                .append("<td>").append(b.hasLimit()
                    ? String.format(Locale.ROOT, "%.1f / %.1f (%d%%)", b.used(), b.limit(), b.percentage())
                    : String.format(Locale.ROOT, "%.1f", b.used()))
                .append("</td>")
                .append("<td>").append(b.currentOverages() > 0
                    ? escapeHtml(String.format(Locale.ROOT, "%.2f %s", b.currentOverages(), b.currency()))
                    : "&mdash;")
                .append("</td>")
                .append("</tr>");
        }
        return """
            <html><body style="font-family: sans-serif;">
            <h2>%s</h2>
            <p>Billing cycle resets %s. Overages are %s.</p>
            <table cellpadding="6" style="border-collapse: collapse;">
            <tr style="text-align: left; border-bottom: 1px solid #888;">
              <th>Resource</th><th>Used / Limit</th><th>Overage charges</th>
            </tr>
            %s
            </table>
            <p style="color: gray; font-size: smaller;">Fetched from <code>kiro-cli acp</code> --
            this is an undocumented CLI internal (issue #94), not a published API, so it may stop
            working on a future kiro-cli release.</p>
            </body></html>
            """.formatted(
                escapeHtml(snapshot.planName()),
                escapeHtml(snapshot.billingCycleReset()),
                snapshot.overagesEnabled() ? "enabled" : "disabled",
                rows);
    }

    private static String buildFallbackHtml(String reason) {
        return """
            <html><body style="font-family: sans-serif;">
            <h2>Usage tracking isn't available here right now</h2>
            <p style="color: gray;">Couldn't fetch live usage from <code>kiro-cli</code>: %s</p>
            <p><b>To check your usage right now:</b></p>
            <ol>
              <li>Run <code>kiro-cli</code></li>
              <li>Type <code>/usage</code></li>
            </ol>
            <p>If you already have the CLI open for other work &mdash; and most people
            using this tool will &mdash; that's the fastest path anyway.</p>
            </body></html>
            """.formatted(escapeHtml(reason));
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}

package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.util.DesktopUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Read-only viewer for a session's actual conversation -- every prompt and
 * reply, in order, from the {@code <uuid>.jsonl} transcript. Fills the gap
 * {@code SessionRawViewerDialog} deliberately leaves open (that one only ever
 * shows the {@code <uuid>.json} sidecar's metadata): finding a session via
 * the manifest table or full-text search was previously a dead end -- the
 * only way to read what was actually said was opening the raw JSONL
 * externally. See issue #120.
 * <p>
 * Reuses {@link SessionManifestParser#parseTranscript} -- the same parse
 * that feeds the FTS index -- rather than re-parsing the file a second,
 * different way. Inherits that parse's scope: only {@code text}-kind
 * message content is shown, never tool call/result payloads (tracked
 * separately as a possible "phase two" on issue #117).
 */
public class SessionTranscriptViewerDialog extends JDialog {

    public SessionTranscriptViewerDialog(Frame parent, SessionManifest manifest) {
        super(parent, "Transcript: " + (manifest.title() != null ? manifest.title() : manifest.sessionId()), false);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JTextArea textArea = new JTextArea(loadTranscriptText(manifest.sourceJsonl()));
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setCaretPosition(0);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton revealButton = new JButton("Reveal File");
        revealButton.addActionListener(e -> DesktopUtils.revealInFileManager(this, manifest.sourceJsonl()));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonRow.add(revealButton);
        buttonRow.add(closeButton);
        add(buttonRow, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(closeButton);
        setPreferredSize(new Dimension(700, 500));
        pack();
        setLocationRelativeTo(parent);
    }

    private static String loadTranscriptText(Path jsonlFile) {
        try {
            List<TranscriptMessage> messages = SessionManifestParser.parseTranscript(jsonlFile, 0).messages();
            return formatTranscript(messages);
        } catch (IOException e) {
            return "Could not read transcript: " + jsonlFile + "\n" + e.getMessage();
        }
    }

    /** Package-private so a unit test can verify formatting without touching Swing. */
    static String formatTranscript(List<TranscriptMessage> messages) {
        if (messages.isEmpty()) {
            return "(No prompts or replies found in this transcript.)";
        }
        StringBuilder text = new StringBuilder();
        for (TranscriptMessage message : messages) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(roleLabel(message.role())).append('\n').append(message.text());
        }
        return text.toString();
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "user" -> "You:";
            case "assistant" -> "Kiro:";
            default -> role + ":";
        };
    }
}

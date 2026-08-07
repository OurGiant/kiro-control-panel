package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.util.DesktopUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only viewer for a session's actual conversation -- every prompt and
 * reply, in order, from the {@code <uuid>.jsonl} transcript -- with an
 * in-dialog "find in page" search bar for jumping between matches. Fills the
 * gap {@code SessionRawViewerDialog} deliberately leaves open (that one only
 * ever shows the {@code <uuid>.json} sidecar's metadata): finding a session
 * via the manifest table or full-text search was previously a dead end -- the
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

    private static final Highlighter.HighlightPainter ALL_MATCH_PAINTER =
        new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 235, 100));
    private static final Highlighter.HighlightPainter CURRENT_MATCH_PAINTER =
        new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 165, 0));

    private final JTextArea textArea = new JTextArea();
    private final JTextField searchField = new JTextField();
    private final JLabel matchCountLabel = new JLabel(" ");
    private final List<Integer> matchOffsets = new ArrayList<>();
    private int currentMatchIndex = -1;
    private String needle = "";

    public SessionTranscriptViewerDialog(Frame parent, SessionManifest manifest) {
        super(parent, "Transcript: " + (manifest.title() != null ? manifest.title() : manifest.sessionId()), false);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildSearchBar(), BorderLayout.NORTH);

        textArea.setText(loadTranscriptText(manifest.sourceJsonl()));
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

    private JPanel buildSearchBar() {
        searchField.putClientProperty("JTextField.placeholderText", "Search in this transcript...");
        searchField.setToolTipText(
            "Type to highlight matches. Press Enter or ▼ for the next match, ▲ for the previous.");
        // Enter-in-field fires this before the dialog's default (Close) button ever sees the
        // key -- same JTextField Enter-key convention SessionsPanel's own filter field uses.
        searchField.addActionListener(e -> jumpToMatch(currentMatchIndex + 1));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                runSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                runSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                runSearch();
            }
        });

        JButton previousButton = new JButton("▲");
        previousButton.setToolTipText("Previous match");
        previousButton.addActionListener(e -> jumpToMatch(currentMatchIndex - 1));
        JButton nextButton = new JButton("▼");
        nextButton.setToolTipText("Next match");
        nextButton.addActionListener(e -> jumpToMatch(currentMatchIndex + 1));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        navButtons.add(previousButton);
        navButtons.add(nextButton);
        navButtons.add(matchCountLabel);

        JPanel bar = new JPanel(new BorderLayout(4, 4));
        bar.add(searchField, BorderLayout.CENTER);
        bar.add(navButtons, BorderLayout.EAST);
        return bar;
    }

    private void runSearch() {
        needle = searchField.getText();
        matchOffsets.clear();
        matchOffsets.addAll(findMatches(textArea.getText(), needle));
        currentMatchIndex = matchOffsets.isEmpty() ? -1 : 0;
        highlightMatches();
        updateMatchCountLabel();
        if (currentMatchIndex >= 0) {
            scrollToMatch(currentMatchIndex);
        }
    }

    private void jumpToMatch(int requestedIndex) {
        if (matchOffsets.isEmpty()) {
            return;
        }
        currentMatchIndex = Math.floorMod(requestedIndex, matchOffsets.size());
        highlightMatches();
        updateMatchCountLabel();
        scrollToMatch(currentMatchIndex);
    }

    private void highlightMatches() {
        Highlighter highlighter = textArea.getHighlighter();
        highlighter.removeAllHighlights();
        if (needle.isEmpty()) {
            return;
        }
        for (int i = 0; i < matchOffsets.size(); i++) {
            int start = matchOffsets.get(i);
            int end = start + needle.length();
            try {
                highlighter.addHighlight(start, end, i == currentMatchIndex ? CURRENT_MATCH_PAINTER : ALL_MATCH_PAINTER);
            } catch (BadLocationException e) {
                // The transcript text is fixed once loaded (read-only, never edited under us),
                // so offsets from findMatches against that same text should always be valid --
                // a failure here means the offset math itself has a bug worth surfacing loudly.
                throw new IllegalStateException("Invalid highlight offset [" + start + "," + end + ")", e);
            }
        }
    }

    private void scrollToMatch(int index) {
        int offset = matchOffsets.get(index);
        textArea.setCaretPosition(offset);
    }

    private void updateMatchCountLabel() {
        if (needle.isEmpty()) {
            matchCountLabel.setText(" ");
        } else if (matchOffsets.isEmpty()) {
            matchCountLabel.setText("No matches");
        } else {
            matchCountLabel.setText((currentMatchIndex + 1) + " of " + matchOffsets.size());
        }
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

    /** Package-private so a unit test can verify match offsets without touching Swing.
     * Case-insensitive, non-overlapping (a match consumes {@code needle}'s full length before
     * searching for the next one), returns start offsets in encounter order. */
    static List<Integer> findMatches(String haystack, String needle) {
        List<Integer> matches = new ArrayList<>();
        if (haystack == null || needle == null || needle.isEmpty()) {
            return matches;
        }
        String lowerHaystack = haystack.toLowerCase(Locale.ROOT);
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        int index = 0;
        while ((index = lowerHaystack.indexOf(lowerNeedle, index)) != -1) {
            matches.add(index);
            index += lowerNeedle.length();
        }
        return matches;
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "user" -> "You:";
            case "assistant" -> "Kiro:";
            default -> role + ":";
        };
    }
}

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
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Read-only viewer for a session's {@code <uuid>.json} sidecar (the manifest
 * metadata -- cwd, timestamps, title, reason). Deliberately not a reuse of
 * {@code util.RawJsonEditorDialog} -- that one is editable and wired into
 * {@code ChangeLogService}/{@code GitAutoCommitter}/self-write tracking, the
 * wrong shape for viewing a file this app doesn't own or write to. For the
 * actual conversation content, see {@link SessionTranscriptViewerDialog}
 * (issue #120) -- this dialog never shows {@code <uuid>.jsonl} content.
 */
public class SessionRawViewerDialog extends JDialog {

    public SessionRawViewerDialog(Frame parent, Path file) {
        super(parent, "Session JSON: " + file.getFileName(), false);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JTextArea textArea = new JTextArea(readFileOrErrorMessage(file));
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, textArea.getFont().getSize()));
        textArea.setCaretPosition(0);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton revealButton = new JButton("Reveal File");
        revealButton.addActionListener(e -> DesktopUtils.revealInFileManager(this, file));
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

    private static String readFileOrErrorMessage(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "Could not read file: " + file + "\n" + e.getMessage();
        }
    }
}

package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.util.DesktopUtils;
import com.ourgiant.kirocontrolpanel.util.LogPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Live-ish view of the app's own current log file, since nothing else
 * surfaces it without leaving the app. Non-modal (so it can stay open
 * alongside normal use) and auto-refreshes on a timer while visible.
 */
public class LogViewerDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(LogViewerDialog.class);
    private static final int REFRESH_INTERVAL_MILLIS = 2000;

    private final JTextArea logArea = new JTextArea();
    private final Timer refreshTimer;
    private long lastLoadedFileSize = -1;

    public LogViewerDialog(Frame parent) {
        super(parent, "Logs — " + LogPaths.currentLogFile(), false);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(760, 480));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> reload(true));
        JButton openFolderButton = new JButton("Open Log Folder...");
        openFolderButton.addActionListener(e -> DesktopUtils.revealInFileManager(this, LogPaths.logsDir()));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        buttonPanel.add(refreshButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        refreshTimer = new Timer(REFRESH_INTERVAL_MILLIS, e -> reload(false));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTimer.start();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                refreshTimer.stop();
            }
        });

        reload(true);
        pack();
        setLocationRelativeTo(parent);
    }

    /** @param force reload even if the file size looks unchanged (used for the explicit Refresh button) */
    private void reload(boolean force) {
        var logFile = LogPaths.currentLogFile();
        try {
            if (!Files.exists(logFile)) {
                logArea.setText("No log file yet — nothing has been logged this run.");
                return;
            }
            long size = Files.size(logFile);
            if (!force && size == lastLoadedFileSize) {
                return; // nothing new since the last tick, skip the re-read/re-render
            }
            lastLoadedFileSize = size;
            logArea.setText(Files.readString(logFile));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        } catch (IOException e) {
            logger.warn("Failed to read log file {}", logFile, e);
            logArea.setText("Failed to read log file: " + e.getMessage());
        }
    }
}

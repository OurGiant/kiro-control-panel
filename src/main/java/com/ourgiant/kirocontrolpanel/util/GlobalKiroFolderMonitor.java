package com.ourgiant.kirocontrolpanel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.stream.Stream;

/**
 * Watches the global {@code ~/.kiro} folder tree (steering docs, skills,
 * agents, mcp.json) for external changes and invokes a callback, debounced.
 * Kiro reads everything under here as instructions -- there are documented
 * cases of adversaries poisoning steering/skills/agents with malicious
 * content -- so the user should be able to notice an unexpected change. See #50.
 * <p>
 * Deliberately a separate watcher from {@link DirectoryWatcher} (which exists
 * to refresh whatever panel is currently in view for whatever scope is
 * currently selected): this one watches the fixed global root unconditionally
 * for the app's whole lifetime, and needs genuine recursive coverage --
 * including subdirectories created after startup (e.g. a new skill folder) --
 * which {@code DirectoryWatcher}'s callers never needed.
 * <p>
 * A change to a path recently written by this app itself (per {@link SelfWriteTracker})
 * is not treated as notification-worthy -- otherwise every routine Global-scope
 * "Save" through the app's own UI would trigger the same alert as an unexpected
 * external change, and the signal would get tuned out fast.
 */
public final class GlobalKiroFolderMonitor {
    private static final Logger logger = LoggerFactory.getLogger(GlobalKiroFolderMonitor.class);
    private static final int DEBOUNCE_MILLIS = 1000;

    private final WatchService watchService;
    private final Timer debounceTimer;

    public GlobalKiroFolderMonitor(Path root, Runnable onChange) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        debounceTimer = new Timer(DEBOUNCE_MILLIS, e -> onChange.run());
        debounceTimer.setRepeats(false);

        if (Files.isDirectory(root)) {
            registerTree(root);
        }

        Thread thread = new Thread(this::pollLoop, "kiro-global-folder-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    private void registerTree(Path root) {
        try (Stream<Path> dirs = Files.walk(root)) {
            dirs.filter(Files::isDirectory).forEach(this::registerQuietly);
        } catch (IOException e) {
            logger.warn("Failed to walk {} for change monitoring", root, e);
        }
    }

    private void registerQuietly(Path dir) {
        try {
            dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            logger.warn("Failed to watch directory {}", dir, e);
        }
    }

    private void pollLoop() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // WatchService doesn't watch subdirectories recursively on its own (notably on
            // Linux/inotify) -- a newly created directory needs its own explicit register()
            // call, or changes inside it (e.g. a brand-new skill folder's SKILL.md) would go
            // unnoticed.
            Path dir = (Path) key.watchable();
            boolean notificationWorthy = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    notificationWorthy = true;
                    continue;
                }
                Path changed = dir.resolve((Path) event.context());
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    registerQuietly(changed);
                }
                if (!SelfWriteTracker.wasRecentlyWrittenByThisApp(changed)) {
                    notificationWorthy = true;
                }
            }
            key.reset();
            if (notificationWorthy) {
                SwingUtilities.invokeLater(debounceTimer::restart);
            }
        }
    }
}

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
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Watches a growing set of directories for external changes (e.g. Kiro IDE
 * itself editing a steering doc while this app is open) and notifies
 * listeners, debounced, so panels can refresh without a manual click.
 *
 * One instance is shared app-wide: registering the same directory twice is a
 * no-op, and directories are never unregistered, since the set stays small
 * (a handful of .kiro subdirectories per pinned workspace) for the life of
 * the app.
 */
public class DirectoryWatcher {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);
    private static final int DEBOUNCE_MILLIS = 300;

    private final WatchService watchService;
    private final Set<Path> registeredPaths = ConcurrentHashMap.newKeySet();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Timer debounceTimer;

    public DirectoryWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        debounceTimer = new Timer(DEBOUNCE_MILLIS, e -> fireListeners());
        debounceTimer.setRepeats(false);

        Thread thread = new Thread(this::pollLoop, "kiro-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Registers a directory for watching if it exists and isn't already registered; safe to call repeatedly. */
    public void watch(Path dir) {
        if (dir == null || !registeredPaths.add(dir)) {
            return;
        }
        if (!Files.isDirectory(dir)) {
            registeredPaths.remove(dir);
            return;
        }
        try {
            dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            registeredPaths.remove(dir);
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
            key.pollEvents(); // we only care *that* something changed, not what
            key.reset();
            SwingUtilities.invokeLater(debounceTimer::restart);
        }
    }

    private void fireListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                logger.warn("File-watch listener threw", e);
            }
        }
    }
}

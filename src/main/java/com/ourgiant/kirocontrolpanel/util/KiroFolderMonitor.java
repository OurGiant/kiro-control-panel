package com.ourgiant.kirocontrolpanel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Watches a {@code .kiro} folder tree (steering docs, skills, agents,
 * mcp.json) for external changes and invokes a callback, debounced, with
 * the actual changed paths. Kiro reads everything under here as
 * instructions -- there are documented cases of adversaries poisoning
 * steering/skills/agents with malicious content -- so the user should be
 * able to notice an unexpected change. See #50.
 * <p>
 * Originally global-root-only ({@code GlobalKiroFolderMonitor}); generalized
 * to accept any root (issue #79) so the change-log feature can run one
 * instance per pinned workspace in addition to the fixed global instance
 * {@code TrayApp} has always constructed. Every existing behavior below is
 * unchanged from that original class.
 * <p>
 * Deliberately a separate watcher from {@link DirectoryWatcher} (which exists
 * to refresh whatever panel is currently in view for whatever scope is
 * currently selected): this one watches a whole root unconditionally for as
 * long as the instance is alive, and needs genuine recursive coverage --
 * including subdirectories created after startup (e.g. a new skill folder) --
 * which {@code DirectoryWatcher}'s callers never needed.
 * <p>
 * A change to a path recently written by this app itself (per {@link SelfWriteTracker})
 * is not treated as notification-worthy -- otherwise every routine Global-scope
 * "Save" through the app's own UI would trigger the same alert as an unexpected
 * external change, and the signal would get tuned out fast.
 * <p>
 * {@code .git} (created by {@link GitAutoCommitter}, #49, when opted in) is never watched
 * or reported on at all, rather than relying on self-write suppression: a single commit
 * writes several files under it (index, COMMIT_EDITMSG, logs/HEAD, refs/, objects/...) that
 * {@link SelfWriteTracker} was never asked to track, since it only marks the one file the
 * app actually saved. It's the auto-commit feature's own plumbing, not Kiro configuration
 * content -- Kiro itself never reads it, so there's nothing here worth alerting on. See #60.
 * <p>
 * Well-known editor transient files (vim swap files, its writability-check {@code 4913}
 * file, emacs autosave/lock files, {@code ~} backup files) are ignored outright, not just
 * suppressed -- they're never real content, so editing a file externally (e.g. in vim)
 * doesn't produce a burst of one alert per swap-file-create/write/rename/swap-file-delete
 * step. See #62.
 * <p>
 * {@code sessions/}, {@code extensions/}, and {@code logs/} are also ignored outright --
 * Kiro-managed, ephemeral state, not configuration content. {@code sessions/}/{@code extensions/}
 * are the same directories {@link GitAutoCommitter} already excludes from its own .gitignore
 * for #49. Kiro CLI writes new files under {@code sessions/cli/} on every launch, and rotates a
 * fresh timestamped directory under {@code logs/} (its own {@code kiro.log}/{@code mcp.log}/
 * {@code powers.log}) on every launch too -- neither is a self-write this app's
 * {@link SelfWriteTracker} can know about (it's a different process), so without these
 * exclusions simply starting kiro-cli tripped the alert every time. See #74 (sessions/
 * extensions) and #128 (logs).
 */
public final class KiroFolderMonitor {
    private static final Logger logger = LoggerFactory.getLogger(KiroFolderMonitor.class);
    private static final int DEBOUNCE_MILLIS = 1000;
    private static final String GIT_INTERNAL_DIR_NAME = ".git";
    private static final Set<String> KIRO_MANAGED_STATE_DIR_NAMES = Set.of("sessions", "extensions", "logs");
    private static final Pattern EDITOR_ARTIFACT_PATTERN = Pattern.compile(
        "^\\..*\\.sw[a-z]$"    // vim swap file, e.g. .SKILL.md.swp
            + "|^4913$"         // vim's writability-check temp file
            + "|^#.*#$"         // emacs autosave file
            + "|^\\.#.*"        // emacs lock file (a symlink, not a real file)
            + "|.*~$"           // vim/emacs backup file
    );

    /** One qualifying (non-self-written, non-excluded) change observed in a single poll iteration. */
    public record ChangeEvent(Path path, WatchEvent.Kind<?> kind) {
    }

    private final WatchService watchService;
    private final Timer debounceTimer;
    private final List<ChangeEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService callbackExecutor;

    public KiroFolderMonitor(Path root, Consumer<List<ChangeEvent>> onChange) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        callbackExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "kiro-folder-monitor-callback");
            thread.setDaemon(true);
            return thread;
        });
        debounceTimer = new Timer(DEBOUNCE_MILLIS, e -> fireCallback(onChange));
        debounceTimer.setRepeats(false);

        if (Files.isDirectory(root)) {
            registerTree(root);
        }

        Thread thread = new Thread(this::pollLoop, "kiro-folder-monitor");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The {@code javax.swing.Timer} that debounces bursts of events fires on the EDT, but
     * consumers of this callback do real file/DB I/O (e.g. {@code ChangeLogService.record},
     * SQLite reindexing) -- so the actual callback dispatch happens on a dedicated background
     * thread instead of inline in the Timer's {@code ActionListener}. See #132.
     */
    private void fireCallback(Consumer<List<ChangeEvent>> onChange) {
        List<ChangeEvent> snapshot;
        synchronized (pendingEvents) {
            snapshot = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
        }
        callbackExecutor.execute(() -> onChange.accept(snapshot));
    }

    /**
     * Closes the underlying {@code WatchService}, which breaks the blocked {@code take()}
     * call in the poll loop's thread via {@code ClosedWatchServiceException} (already caught
     * there to exit cleanly). Needed now that instances come and go per pinned/unpinned
     * workspace (issue #79's {@code ChangeLogWatcherManager}), not just the one fixed,
     * app-lifetime global instance this class originally had.
     */
    public void close() {
        try {
            watchService.close();
        } catch (IOException e) {
            logger.warn("Failed to close folder monitor watch service", e);
        }
        callbackExecutor.shutdownNow();
    }

    private void registerTree(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isGitInternal(dir) || isKiroManagedStateDir(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    registerQuietly(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to walk {} for change monitoring", root, e);
        }
    }

    private static boolean isGitInternal(Path path) {
        return GIT_INTERNAL_DIR_NAME.equals(String.valueOf(path.getFileName()));
    }

    private static boolean isKiroManagedStateDir(Path path) {
        return KIRO_MANAGED_STATE_DIR_NAMES.contains(String.valueOf(path.getFileName()));
    }

    private static boolean isEditorArtifact(Path path) {
        return EDITOR_ARTIFACT_PATTERN.matcher(String.valueOf(path.getFileName())).matches();
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
                    logger.info("External change detected: watch overflow (some events may have been dropped)");
                    notificationWorthy = true;
                    continue;
                }
                Path changed = dir.resolve((Path) event.context());
                if (isGitInternal(changed)) {
                    // GitAutoCommitter's own plumbing (see class Javadoc) -- never register a
                    // watch on it and never let its creation itself count as notification-worthy.
                    continue;
                }
                if (isKiroManagedStateDir(changed)) {
                    // Kiro's own ephemeral session/extension state (see class Javadoc) -- same
                    // treatment as .git above.
                    continue;
                }
                if (isEditorArtifact(changed)) {
                    // Not real content, just an editor's own bookkeeping (see class Javadoc).
                    continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    registerQuietly(changed);
                }
                if (!SelfWriteTracker.wasRecentlyWrittenByThisApp(changed)) {
                    logger.info("External change detected: {} ({})", changed, event.kind().name());
                    pendingEvents.add(new ChangeEvent(changed, event.kind()));
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

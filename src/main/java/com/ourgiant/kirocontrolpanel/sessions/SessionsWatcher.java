package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.util.KiroFolderMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches {@code ~/.kiro/sessions/cli} for kiro-cli writing new sessions or
 * appending to existing ones, and incrementally reindexes just the affected
 * session(s). Its own, dedicated {@link KiroFolderMonitor} instance --
 * deliberately not reusing {@code TrayApp}'s global change-alert monitor or
 * {@code ChangeLogWatcherManager}'s, same "complete isolation" precedent
 * {@code ChangeLogWatcherManager} itself already established for not reusing
 * the global one either.
 * <p>
 * Must be rooted at {@code sessions/cli} itself, not {@code sessions} -- see
 * {@link com.ourgiant.kirocontrolpanel.KiroPaths#globalSessionsCliDir()}'s
 * Javadoc for why.
 */
public final class SessionsWatcher {
    private static final Logger logger = LoggerFactory.getLogger(SessionsWatcher.class);
    private static final String JSON_SUFFIX = ".json";
    private static final String JSONL_SUFFIX = ".jsonl";

    private final Path sessionsCliDir;
    private final SessionIndexService indexService;
    private final AtomicReference<KiroFolderMonitor> monitor = new AtomicReference<>();

    public SessionsWatcher(Path sessionsCliDir, SessionIndexService indexService) {
        this.sessionsCliDir = sessionsCliDir;
        this.indexService = indexService;
    }

    /** No explicit {@code stop()} call site exists anywhere in this app for a background
     * watcher (see {@code SnapshotScheduler}/{@code ChangeLogWatcherManager}) -- {@link #close()}
     * is provided for tests and symmetry, but relies on being a daemon thread that dies with
     * the JVM in production, same convention. */
    public void start() throws IOException {
        monitor.set(new KiroFolderMonitor(sessionsCliDir, this::onChange));
    }

    public void close() {
        KiroFolderMonitor active = monitor.getAndSet(null);
        if (active != null) {
            active.close();
        }
    }

    private void onChange(List<KiroFolderMonitor.ChangeEvent> events) {
        Set<Path> sidecarsToReindex = new LinkedHashSet<>();
        for (KiroFolderMonitor.ChangeEvent event : events) {
            Path path = event.path();
            String name = path.getFileName().toString();
            if (name.endsWith(JSON_SUFFIX)) {
                sidecarsToReindex.add(path);
            } else if (name.endsWith(JSONL_SUFFIX)) {
                String stem = name.substring(0, name.length() - JSONL_SUFFIX.length());
                sidecarsToReindex.add(path.resolveSibling(stem + JSON_SUFFIX));
            }
            // .history files carry no manifest/FTS-relevant content -- ignored.
        }
        for (Path sidecar : sidecarsToReindex) {
            reindexOrRemove(sidecar);
        }
    }

    private void reindexOrRemove(Path sidecar) {
        try {
            if (Files.isRegularFile(sidecar)) {
                indexService.indexOne(sidecar);
            } else {
                indexService.removeSession(stemOf(sidecar));
            }
        } catch (IOException | SQLException e) {
            logger.warn("Failed to live-reindex session file {}", sidecar, e);
        }
    }

    private static String stemOf(Path jsonSidecar) {
        String name = jsonSidecar.getFileName().toString();
        return name.endsWith(JSON_SUFFIX) ? name.substring(0, name.length() - JSON_SUFFIX.length()) : name;
    }
}

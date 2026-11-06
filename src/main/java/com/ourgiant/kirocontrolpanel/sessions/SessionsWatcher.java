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
 * Watches kiro-cli's session stores for new sessions or appends to existing ones, and
 * incrementally reindexes just the affected session(s). Its own, dedicated
 * {@link KiroFolderMonitor} instance -- deliberately not reusing {@code TrayApp}'s global
 * change-alert monitor or {@code ChangeLogWatcherManager}'s.
 * <p>
 * Rooted at {@code ~/.kiro/sessions} so one watcher covers both the V2 store
 * ({@code sessions/cli/<uuid>.json|.jsonl}) and the V3 store
 * ({@code sessions/<hash>/sess_*}{@code /session.json|messages.jsonl}, issue #147). That needs
 * the monitor's skip of directories named {@code sessions} turned off -- see
 * {@link KiroFolderMonitor#KiroFolderMonitor(Path, java.util.function.Consumer, boolean)}. Rooting
 * it directly at a V2-only {@code cli} directory still works too (tests do this).
 */
public final class SessionsWatcher {
    private static final Logger logger = LoggerFactory.getLogger(SessionsWatcher.class);
    private static final String JSON_SUFFIX = ".json";
    private static final String JSONL_SUFFIX = ".jsonl";

    private final Path sessionsRoot;
    private final SessionIndexService indexService;
    private final AtomicReference<KiroFolderMonitor> monitor = new AtomicReference<>();

    public SessionsWatcher(Path sessionsRoot, SessionIndexService indexService) {
        this.sessionsRoot = sessionsRoot;
        this.indexService = indexService;
    }

    /** No explicit {@code stop()} call site exists anywhere in this app for a background
     * watcher (see {@code SnapshotScheduler}/{@code ChangeLogWatcherManager}) -- {@link #close()}
     * is provided for tests and symmetry, but relies on being a daemon thread that dies with
     * the JVM in production, same convention. */
    public void start() throws IOException {
        monitor.set(new KiroFolderMonitor(sessionsRoot, this::onChange, false));
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
            collectAffectedSidecars(event.path(), sidecarsToReindex);
        }
        for (Path sidecar : sidecarsToReindex) {
            reindexOrRemove(sidecar);
        }
    }

    /** Maps one changed path to the sidecar(s) whose index rows it affects. Package-private for tests. */
    void collectAffectedSidecars(Path path, Set<Path> out) {
        Path v3SessionDir = enclosingV3SessionDir(path);
        if (v3SessionDir != null) {
            // Only session.json/messages.jsonl (or the session folder itself appearing or going
            // away) matter. sub-executions/ and the *.cursor files carry nothing indexed.
            boolean isSessionDir = path.equals(v3SessionDir);
            String name = path.getFileName().toString();
            boolean isIndexedFile = path.getParent().equals(v3SessionDir)
                && (SessionEngine.V3_SIDECAR_NAME.equals(name) || SessionEngine.V3_TRANSCRIPT_NAME.equals(name));
            if (isSessionDir || isIndexedFile) {
                out.add(v3SessionDir.resolve(SessionEngine.V3_SIDECAR_NAME));
            }
            return;
        }
        String name = path.getFileName().toString();
        // V2 files count only directly inside the cli store (or the watch root itself, when
        // a test roots the watcher at a cli folder), never stray JSON elsewhere under sessions/.
        Path parent = path.getParent();
        boolean inV2Store = parent != null && (parent.equals(sessionsRoot)
            || (SessionEngine.V2_STORE_DIR_NAME.equals(String.valueOf(parent.getFileName()))
                && sessionsRoot.equals(parent.getParent())));
        if (inV2Store && name.endsWith(JSON_SUFFIX)) {
            out.add(path);
        } else if (inV2Store && name.endsWith(JSONL_SUFFIX)) {
            String stem = name.substring(0, name.length() - JSONL_SUFFIX.length());
            out.add(path.resolveSibling(stem + JSON_SUFFIX));
        } else if (Files.isDirectory(path) && sessionsRoot.equals(path.getParent())) {
            // A new V3 workspace folder. Its sess_* folder and files can be written before the
            // monitor registers a watch on it, so pick up whatever is already inside.
            try {
                out.addAll(SessionIndexService.listV3SidecarsInWorkspaceDir(path));
            } catch (IOException e) {
                logger.warn("Failed to scan new session folder {}", path, e);
            }
        }
        // .history files and anything else carry no manifest/FTS-relevant content -- ignored.
    }

    /** The {@code sess_*} folder at or above {@code path} (stopping at the watch root), or null. */
    private Path enclosingV3SessionDir(Path path) {
        for (Path p = path; p != null && !p.equals(sessionsRoot); p = p.getParent()) {
            if (SessionEngine.isV3SessionDir(p)) {
                return p;
            }
        }
        return null;
    }

    private void reindexOrRemove(Path sidecar) {
        try {
            if (Files.isRegularFile(sidecar)) {
                indexService.indexOne(sidecar);
            } else {
                indexService.removeSession(SessionEngine.ofSidecar(sidecar).sessionIdFor(sidecar));
            }
        } catch (IOException | SQLException e) {
            logger.warn("Failed to live-reindex session file {}", sidecar, e);
        }
    }
}

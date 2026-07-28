package com.ourgiant.kirocontrolpanel.changelog;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.util.KiroFolderMonitor;
import com.ourgiant.kirocontrolpanel.util.WorkspaceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns a {@link KiroFolderMonitor} for Global plus one for every currently
 * pinned workspace, feeding every genuinely-external change to
 * {@link ChangeLogService}. Deliberately a second, independent watcher set
 * from {@code TrayApp}'s existing global monitor (issue #50's external-change
 * notification feature) -- full isolation from that already-tested,
 * security-relevant feature, at the cost of one extra thread/{@code WatchService}
 * watching the global tree twice. See issue #79.
 * <p>
 * {@link WorkspaceRegistry} only broadcasts "the pinned set changed," with no
 * payload, so per-workspace monitors are kept in sync by diffing the pinned
 * set against what was last seen -- new workspaces get a new monitor, removed
 * ones get theirs {@link KiroFolderMonitor#close() closed}.
 */
public class ChangeLogWatcherManager {
    private static final Logger logger = LoggerFactory.getLogger(ChangeLogWatcherManager.class);

    private final AppPreferences preferences;
    private final Map<String, KiroFolderMonitor> workspaceMonitors = new ConcurrentHashMap<>();
    private Set<String> lastKnownWorkspaces = Set.of();

    public ChangeLogWatcherManager(AppPreferences preferences) {
        this.preferences = preferences;
    }

    public void start() {
        try {
            new KiroFolderMonitor(KiroPaths.globalKiroHome(), this::recordBatch);
        } catch (IOException e) {
            logger.warn("Failed to start change-log global folder monitor", e);
        }

        Set<String> initial = preferences.getWorkspaces();
        for (String workspace : initial) {
            startWorkspaceMonitor(workspace);
        }
        lastKnownWorkspaces = new HashSet<>(initial);

        WorkspaceRegistry.addListener(this::syncWorkspaceMonitors);
    }

    private void syncWorkspaceMonitors() {
        Set<String> current = preferences.getWorkspaces();
        WorkspaceDiff diff = diff(lastKnownWorkspaces, current);
        for (String added : diff.added()) {
            startWorkspaceMonitor(added);
        }
        for (String removed : diff.removed()) {
            KiroFolderMonitor monitor = workspaceMonitors.remove(removed);
            if (monitor != null) {
                monitor.close();
            }
        }
        lastKnownWorkspaces = new HashSet<>(current);
    }

    private void startWorkspaceMonitor(String workspace) {
        try {
            Path kiroDir = Paths.get(workspace).resolve(".kiro");
            workspaceMonitors.put(workspace, new KiroFolderMonitor(kiroDir, this::recordBatch));
        } catch (IOException e) {
            logger.warn("Failed to start change-log watcher for workspace {}", workspace, e);
        }
    }

    private void recordBatch(List<KiroFolderMonitor.ChangeEvent> events) {
        for (KiroFolderMonitor.ChangeEvent event : events) {
            ChangeKind kind = mapKind(event.kind());
            if (kind != null) {
                ChangeLogService.record(event.path(), kind, ChangeSource.EXTERNAL);
            }
        }
    }

    private static ChangeKind mapKind(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return ChangeKind.CREATED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return ChangeKind.MODIFIED;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return ChangeKind.DELETED;
        }
        return null;
    }

    /** Package-private, for tests: pure add/remove diffing, independent of AppPreferences/WorkspaceRegistry. */
    record WorkspaceDiff(Set<String> added, Set<String> removed) {
    }

    static WorkspaceDiff diff(Set<String> oldWorkspaces, Set<String> newWorkspaces) {
        Set<String> added = new HashSet<>(newWorkspaces);
        added.removeAll(oldWorkspaces);
        Set<String> removed = new HashSet<>(oldWorkspaces);
        removed.removeAll(newWorkspaces);
        return new WorkspaceDiff(added, removed);
    }
}

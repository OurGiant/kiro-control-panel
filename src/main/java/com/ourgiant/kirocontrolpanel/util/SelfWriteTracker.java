package com.ourgiant.kirocontrolpanel.util;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks files this app just wrote to itself, so {@link GlobalKiroFolderMonitor}
 * can tell "the user just saved this through Kiro Control Panel's own UI"
 * apart from "something else touched it" and only alert on the latter. See #50.
 * <p>
 * The suppression window is deliberately generous, not just "long enough for
 * a fast local write": {@code java.nio.file.WatchService}'s macOS backend
 * polls rather than using native OS events and can take several seconds to
 * deliver a change (see the generous timeouts in DirectoryWatcherTest and
 * GlobalKiroFolderMonitorTest) -- too short a window would let a self-write's
 * own delayed event slip past suppression and notify anyway.
 */
public final class SelfWriteTracker {
    private static final Duration SUPPRESSION_WINDOW = Duration.ofSeconds(10);
    private static final Map<Path, Instant> recentWrites = new ConcurrentHashMap<>();

    private SelfWriteTracker() {
    }

    /** Call right before writing {@code path}, so the suppression window is active before the OS could possibly emit the change event. */
    public static void markAboutToWrite(Path path) {
        recentWrites.put(path.toAbsolutePath().normalize(), Instant.now());
    }

    public static boolean wasRecentlyWrittenByThisApp(Path path) {
        Instant when = recentWrites.get(path.toAbsolutePath().normalize());
        return when != null && Duration.between(when, Instant.now()).compareTo(SUPPRESSION_WINDOW) <= 0;
    }
}

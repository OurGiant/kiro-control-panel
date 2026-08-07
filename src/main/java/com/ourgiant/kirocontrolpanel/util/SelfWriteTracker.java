package com.ourgiant.kirocontrolpanel.util;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks files this app just wrote to itself, so {@link KiroFolderMonitor}
 * can tell "the user just saved this through Kiro Control Panel's own UI"
 * apart from "something else touched it" and only alert on the latter. See #50.
 * <p>
 * The suppression window is deliberately generous, not just "long enough for
 * a fast local write": {@code java.nio.file.WatchService}'s macOS backend
 * polls rather than using native OS events and can take several seconds to
 * deliver a change (see the generous timeouts in DirectoryWatcherTest and
 * KiroFolderMonitorTest) -- too short a window would let a self-write's
 * own delayed event slip past suppression and notify anyway.
 */
public final class SelfWriteTracker {
    // Package-private (not private): SelfWriteTrackerTest references it directly to seed an
    // already-expired entry, rather than hardcoding a duplicate "10 seconds" or actually
    // waiting one out.
    static final Duration SUPPRESSION_WINDOW = Duration.ofSeconds(10);
    private static final Map<Path, Instant> recentWrites = new ConcurrentHashMap<>();

    private SelfWriteTracker() {
    }

    /** Call right before writing {@code path}, so the suppression window is active before the OS could possibly emit the change event. */
    public static void markAboutToWrite(Path path) {
        Instant now = Instant.now();
        recentWrites.put(path.toAbsolutePath().normalize(), now);
        evictExpired(now);
    }

    public static boolean wasRecentlyWrittenByThisApp(Path path) {
        Instant when = recentWrites.get(path.toAbsolutePath().normalize());
        return when != null && Duration.between(when, Instant.now()).compareTo(SUPPRESSION_WINDOW) <= 0;
    }

    /**
     * Opportunistic sweep, piggybacked on every write rather than a dedicated background
     * thread/timer -- without this the map only ever grows for the lifetime of the app,
     * one entry per distinct file path ever saved through it.
     */
    private static void evictExpired(Instant now) {
        recentWrites.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(SUPPRESSION_WINDOW) > 0);
    }

    /** Package-private, for tests: seeds a raw entry bypassing the eviction sweep, to simulate an already-expired mark without waiting out the real suppression window. */
    static void putRawEntry(Path path, Instant when) {
        recentWrites.put(path.toAbsolutePath().normalize(), when);
    }

    /** Package-private, for tests: current tracked-entry count, to confirm eviction actually shrinks the map. */
    static int trackedEntryCount() {
        return recentWrites.size();
    }
}

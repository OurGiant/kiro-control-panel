package com.ourgiant.kirocontrolpanel.snapshot;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Periodically snapshots the global {@code ~/.kiro} tree per issue #91.
 * Polls on a fixed, short interval rather than reconfiguring a single Timer
 * to the user's chosen frequency -- so a frequency/destination/enabled change
 * made in Settings while the app is running takes effect on the very next
 * tick, with no need to restart the timer. Each tick's actual work (zip +
 * prune) runs on a daemon background thread, not the EDT, matching
 * {@code KiroFolderMonitor}'s own poll-thread convention -- unlike that
 * class's file-system watch, this only touches disk every {@link #POLL_MILLIS},
 * so a plain thread per due snapshot is simpler than a persistent worker.
 */
public class SnapshotScheduler {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotScheduler.class);
    private static final int POLL_MILLIS = 60_000;

    private final AppPreferences preferences;
    private final Timer pollTimer;

    public SnapshotScheduler(AppPreferences preferences) {
        this.preferences = preferences;
        this.pollTimer = new Timer(POLL_MILLIS, e -> checkAndSnapshotIfDue());
    }

    /** Checks immediately (covers "stale at app start"), then keeps polling while running. */
    public void start() {
        checkAndSnapshotIfDue();
        pollTimer.start();
    }

    private void checkAndSnapshotIfDue() {
        if (!preferences.isSnapshotEnabled()) {
            return;
        }
        String destination = preferences.getSnapshotDestination();
        if (destination.isBlank()) {
            return;
        }
        Path destinationDir = Paths.get(destination);
        Optional<Instant> latest = SnapshotService.latestSnapshotTime(destinationDir);
        if (!isDue(latest, Instant.now(), preferences.getSnapshotFrequencyMinutes())) {
            return;
        }

        Thread thread = new Thread(() -> runSnapshot(destinationDir), "kiro-snapshot-scheduler");
        thread.setDaemon(true);
        thread.start();
    }

    private void runSnapshot(Path destinationDir) {
        try {
            SnapshotService.createSnapshot(KiroPaths.globalKiroHome(), destinationDir);
            SnapshotService.pruneOldSnapshots(destinationDir, preferences.getSnapshotRetentionCount());
        } catch (IOException e) {
            logger.warn("Scheduled .kiro snapshot failed", e);
        }
    }

    /** Package-private, for tests: pure due-or-not comparison, independent of preferences/filesystem. */
    static boolean isDue(Optional<Instant> latestSnapshotTime, Instant now, int frequencyMinutes) {
        if (latestSnapshotTime.isEmpty()) {
            return true;
        }
        Duration sinceLatest = Duration.between(latestSnapshotTime.get(), now);
        return sinceLatest.compareTo(Duration.ofMinutes(frequencyMinutes)) >= 0;
    }
}

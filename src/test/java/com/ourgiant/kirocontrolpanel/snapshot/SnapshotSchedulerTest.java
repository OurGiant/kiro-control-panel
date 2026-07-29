package com.ourgiant.kirocontrolpanel.snapshot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pure due-or-not comparison directly, independent of real timers/filesystem --
 * matches this app's established split between pure, testable logic and thin, untested
 * wiring glue (e.g. {@code ChangeLogWatcherManager.diff}).
 */
class SnapshotSchedulerTest {

    @Test
    void isDueWhenNoSnapshotHasEverBeenTaken() {
        assertTrue(SnapshotScheduler.isDue(Optional.empty(), Instant.now(), 1440));
    }

    @Test
    void isDueWhenLatestSnapshotOlderThanFrequency() {
        Instant now = Instant.now();
        Instant latest = now.minus(Duration.ofMinutes(90));

        assertTrue(SnapshotScheduler.isDue(Optional.of(latest), now, 60));
    }

    @Test
    void isNotDueWhenLatestSnapshotWithinFrequency() {
        Instant now = Instant.now();
        Instant latest = now.minus(Duration.ofMinutes(10));

        assertFalse(SnapshotScheduler.isDue(Optional.of(latest), now, 60));
    }

    @Test
    void isDueExactlyAtFrequencyBoundary() {
        Instant now = Instant.now();
        Instant latest = now.minus(Duration.ofMinutes(60));

        assertTrue(SnapshotScheduler.isDue(Optional.of(latest), now, 60));
    }
}

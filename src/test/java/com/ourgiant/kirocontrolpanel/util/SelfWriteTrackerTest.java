package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfWriteTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void aPathNeverMarkedIsNotConsideredASelfWrite() {
        assertFalse(SelfWriteTracker.wasRecentlyWrittenByThisApp(tempDir.resolve("never-written.json")));
    }

    @Test
    void aMarkedPathIsConsideredASelfWrite() {
        Path path = tempDir.resolve("mcp.json");

        SelfWriteTracker.markAboutToWrite(path);

        assertTrue(SelfWriteTracker.wasRecentlyWrittenByThisApp(path));
    }

    @Test
    void marksAreTrackedPerExactPathNotGlobally() {
        Path written = tempDir.resolve("written.json");
        Path untouched = tempDir.resolve("untouched.json");

        SelfWriteTracker.markAboutToWrite(written);

        assertTrue(SelfWriteTracker.wasRecentlyWrittenByThisApp(written));
        assertFalse(SelfWriteTracker.wasRecentlyWrittenByThisApp(untouched));
    }

    @Test
    void relativeAndAbsoluteFormsOfTheSamePathBothMatch() {
        Path absolute = tempDir.resolve("mcp.json");
        Path relativeEquivalent = absolute.getParent().resolve(".").resolve("mcp.json");

        SelfWriteTracker.markAboutToWrite(absolute);

        assertTrue(SelfWriteTracker.wasRecentlyWrittenByThisApp(relativeEquivalent));
    }

    @Test
    void expiredEntriesAreEvictedRatherThanAccumulatingForever() {
        Instant longExpired = Instant.now().minus(SelfWriteTracker.SUPPRESSION_WINDOW).minusSeconds(5);
        Path staleA = tempDir.resolve("stale-a.json");
        Path staleB = tempDir.resolve("stale-b.json");
        SelfWriteTracker.putRawEntry(staleA, longExpired);
        SelfWriteTracker.putRawEntry(staleB, longExpired);
        int countWithStaleEntriesPresent = SelfWriteTracker.trackedEntryCount();

        // Any subsequent write sweeps expired entries as a side effect (see SelfWriteTracker's
        // evictExpired) -- no dedicated background thread/timer needed to reclaim them.
        SelfWriteTracker.markAboutToWrite(tempDir.resolve("trigger-eviction.json"));

        assertFalse(SelfWriteTracker.wasRecentlyWrittenByThisApp(staleA));
        assertFalse(SelfWriteTracker.wasRecentlyWrittenByThisApp(staleB));
        assertTrue(SelfWriteTracker.trackedEntryCount() <= countWithStaleEntriesPresent - 1,
            "map should shrink once expired entries are swept, not just grow forever (was "
                + countWithStaleEntriesPresent + ", now " + SelfWriteTracker.trackedEntryCount() + ")");
    }
}

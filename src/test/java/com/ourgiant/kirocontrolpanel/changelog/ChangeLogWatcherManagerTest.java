package com.ourgiant.kirocontrolpanel.changelog;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pure add/remove diffing logic directly, independent of real
 * {@code AppPreferences}/{@code WorkspaceRegistry} -- matches this app's
 * established split between pure, testable logic and thin, untested wiring
 * glue (e.g. {@code KiroSessionLauncher}'s command builder vs. its launcher).
 */
class ChangeLogWatcherManagerTest {

    @Test
    void detectsNewlyAddedWorkspace() {
        ChangeLogWatcherManager.WorkspaceDiff diff =
            ChangeLogWatcherManager.diff(Set.of("/a"), Set.of("/a", "/b"));

        assertEquals(Set.of("/b"), diff.added());
        assertTrue(diff.removed().isEmpty());
    }

    @Test
    void detectsRemovedWorkspace() {
        ChangeLogWatcherManager.WorkspaceDiff diff =
            ChangeLogWatcherManager.diff(Set.of("/a", "/b"), Set.of("/a"));

        assertTrue(diff.added().isEmpty());
        assertEquals(Set.of("/b"), diff.removed());
    }

    @Test
    void detectsSimultaneousAddAndRemove() {
        ChangeLogWatcherManager.WorkspaceDiff diff =
            ChangeLogWatcherManager.diff(Set.of("/a"), Set.of("/b"));

        assertEquals(Set.of("/b"), diff.added());
        assertEquals(Set.of("/a"), diff.removed());
    }

    @Test
    void noChangeYieldsEmptyDiff() {
        ChangeLogWatcherManager.WorkspaceDiff diff =
            ChangeLogWatcherManager.diff(Set.of("/a", "/b"), Set.of("/a", "/b"));

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
    }
}

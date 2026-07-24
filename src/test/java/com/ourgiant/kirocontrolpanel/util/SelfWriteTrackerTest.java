package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
}

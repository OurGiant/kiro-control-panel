package com.ourgiant.kirocontrolpanel.changelog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLogServiceTest {

    @TempDir
    Path tempDir;

    private Path logFile() {
        return tempDir.resolve("change-log.jsonl");
    }

    private Path workspaceRoot() {
        return tempDir.resolve("workspace");
    }

    @Test
    void recordThenLoadSinceRoundTrips() {
        Path filePath = workspaceRoot().resolve(".kiro").resolve("steering").resolve("tech.md");

        ChangeLogService.record(logFile(), filePath, ChangeKind.CREATED, ChangeSource.SELF);
        List<ChangeLogEntry> entries = ChangeLogService.loadSince(logFile(), Instant.EPOCH);

        assertEquals(1, entries.size());
        ChangeLogEntry entry = entries.get(0);
        assertEquals("STEERING", entry.surface());
        assertEquals(ChangeKind.CREATED, entry.kindEnum());
        assertEquals(ChangeSource.SELF, entry.sourceEnum());
        assertFalse(entry.global());
        assertEquals(workspaceRoot().toString(), entry.scopeLabel());
    }

    @Test
    void recordSkipsUnrecognizedPaths() {
        Path unrelated = tempDir.resolve("not-under-kiro.txt");

        ChangeLogService.record(logFile(), unrelated, ChangeKind.MODIFIED, ChangeSource.SELF);

        assertTrue(ChangeLogService.loadSince(logFile(), Instant.EPOCH).isEmpty());
    }

    @Test
    void loadSinceFiltersOutEntriesBeforeCutoff() throws IOException {
        Instant now = Instant.now();
        writeRawLine(now.minus(10, ChronoUnit.DAYS), "MCP", "old");
        writeRawLine(now.minus(1, ChronoUnit.HOURS), "MCP", "recent");

        List<ChangeLogEntry> entries = ChangeLogService.loadSince(logFile(), now.minus(1, ChronoUnit.DAYS));

        assertEquals(1, entries.size());
        assertEquals("recent", entries.get(0).filePath());
    }

    @Test
    void loadSinceReturnsNewestFirst() throws IOException {
        Instant now = Instant.now();
        writeRawLine(now.minus(2, ChronoUnit.HOURS), "MCP", "older");
        writeRawLine(now.minus(1, ChronoUnit.HOURS), "MCP", "newer");

        List<ChangeLogEntry> entries = ChangeLogService.loadSince(logFile(), Instant.EPOCH);

        assertEquals(List.of("newer", "older"), entries.stream().map(ChangeLogEntry::filePath).toList());
    }

    @Test
    void loadSinceSkipsUnparseableLinesWithoutFailingTheWholeLoad() throws IOException {
        Files.createDirectories(logFile().getParent());
        Files.writeString(logFile(), "{ not valid json\n", java.nio.file.StandardOpenOption.CREATE);
        writeRawLine(Instant.now(), "MCP", "valid-entry");

        List<ChangeLogEntry> entries = ChangeLogService.loadSince(logFile(), Instant.EPOCH);

        assertEquals(1, entries.size());
        assertEquals("valid-entry", entries.get(0).filePath());
    }

    @Test
    void pruneOlderThanKeepsRecentAndDropsOld() throws IOException {
        Instant now = Instant.now();
        writeRawLine(now.minus(200, ChronoUnit.DAYS), "MCP", "ancient");
        writeRawLine(now.minus(1, ChronoUnit.DAYS), "MCP", "recent");

        ChangeLogService.pruneOlderThan(logFile(), now.minus(90, ChronoUnit.DAYS));
        List<ChangeLogEntry> entries = ChangeLogService.loadSince(logFile(), Instant.EPOCH);

        assertEquals(1, entries.size());
        assertEquals("recent", entries.get(0).filePath());
    }

    @Test
    void loadSinceOnMissingFileReturnsEmpty() {
        assertTrue(ChangeLogService.loadSince(logFile(), Instant.EPOCH).isEmpty());
    }

    private void writeRawLine(Instant timestamp, String surface, String filePath) throws IOException {
        Files.createDirectories(logFile().getParent());
        String json = """
            {"timestampEpochMilli":%d,"surface":"%s","scopeLabel":"Global","global":true,"filePath":"%s","kind":"CREATED","source":"SELF"}
            """.formatted(timestamp.toEpochMilli(), surface, filePath).stripTrailing();
        Files.writeString(logFile(), json + System.lineSeparator(),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}

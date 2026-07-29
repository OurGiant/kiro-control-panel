package com.ourgiant.kirocontrolpanel.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotServiceTest {

    @Test
    void createSnapshotIncludesManagedContentAndExcludesSessionsAndExtensions(@TempDir Path tempDir) throws IOException {
        Path kiroHome = tempDir.resolve(".kiro");
        Files.createDirectories(kiroHome.resolve("steering"));
        Files.writeString(kiroHome.resolve("steering").resolve("notes.md"), "hello", StandardCharsets.UTF_8);
        Files.createDirectories(kiroHome.resolve("sessions").resolve("cli"));
        Files.writeString(kiroHome.resolve("sessions").resolve("cli").resolve("state.json"), "{}", StandardCharsets.UTF_8);
        Files.createDirectories(kiroHome.resolve("extensions"));
        Files.writeString(kiroHome.resolve("extensions").resolve("ext.json"), "{}", StandardCharsets.UTF_8);
        Files.createDirectories(kiroHome.resolve(".git").resolve("objects"));
        Files.writeString(kiroHome.resolve(".git").resolve("HEAD"), "ref: refs/heads/main", StandardCharsets.UTF_8);

        Path destinationDir = tempDir.resolve("backups");
        Path snapshot = SnapshotService.createSnapshot(kiroHome, destinationDir);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        assertTrue(entries.contains("steering/notes.md"));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("sessions/")));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("extensions/")));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith(".git/")));
    }

    @Test
    void createSnapshotPreservesFileContent(@TempDir Path tempDir) throws IOException {
        Path kiroHome = tempDir.resolve(".kiro");
        Files.createDirectories(kiroHome);
        Files.writeString(kiroHome.resolve("mcp.json"), "{\"servers\":[]}", StandardCharsets.UTF_8);

        Path snapshot = SnapshotService.createSnapshot(kiroHome, tempDir.resolve("backups"));

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(snapshot))) {
            ZipEntry entry = zis.getNextEntry();
            assertEquals("mcp.json", entry.getName());
            String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"servers\":[]}", content);
        }
    }

    @Test
    void latestSnapshotTimeIsEmptyWhenNoneExist(@TempDir Path tempDir) {
        assertTrue(SnapshotService.latestSnapshotTime(tempDir.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void latestSnapshotTimeReflectsNewestFilename(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir);
        Files.createFile(tempDir.resolve("kiro-snapshot-20260101-120000.zip"));
        Files.createFile(tempDir.resolve("kiro-snapshot-20260615-093000.zip"));
        Files.createFile(tempDir.resolve("not-a-snapshot.zip"));

        Optional<Instant> latest = SnapshotService.latestSnapshotTime(tempDir);

        assertTrue(latest.isPresent());
        Instant expected = java.time.LocalDateTime.of(2026, 6, 15, 9, 30, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant();
        assertEquals(expected, latest.get());
    }

    @Test
    void pruneOldSnapshotsKeepsOnlyNewestN(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("kiro-snapshot-20260101-000000.zip"));
        Files.createFile(tempDir.resolve("kiro-snapshot-20260102-000000.zip"));
        Files.createFile(tempDir.resolve("kiro-snapshot-20260103-000000.zip"));
        Files.createFile(tempDir.resolve("kiro-snapshot-20260104-000000.zip"));

        SnapshotService.pruneOldSnapshots(tempDir, 2);

        assertFalse(Files.exists(tempDir.resolve("kiro-snapshot-20260101-000000.zip")));
        assertFalse(Files.exists(tempDir.resolve("kiro-snapshot-20260102-000000.zip")));
        assertTrue(Files.exists(tempDir.resolve("kiro-snapshot-20260103-000000.zip")));
        assertTrue(Files.exists(tempDir.resolve("kiro-snapshot-20260104-000000.zip")));
    }

    @Test
    void createSnapshotSkipsDestinationDirNestedInsideKiroHome(@TempDir Path tempDir) throws IOException {
        Path kiroHome = tempDir.resolve(".kiro");
        Files.createDirectories(kiroHome);
        Files.writeString(kiroHome.resolve("mcp.json"), "{}", StandardCharsets.UTF_8);
        Path destinationDir = kiroHome.resolve("snapshots");

        Path snapshot = SnapshotService.createSnapshot(kiroHome, destinationDir);

        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(snapshot))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        assertTrue(entries.contains("mcp.json"));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("snapshots/")));
    }
}

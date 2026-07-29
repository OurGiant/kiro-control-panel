package com.ourgiant.kirocontrolpanel.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates and prunes compressed-archive snapshots of the global {@code ~/.kiro}
 * tree for issue #91. {@code sessions/} and {@code extensions/} are excluded --
 * the same "Kiro-managed, ephemeral session state" directories already excluded
 * by {@link com.ourgiant.kirocontrolpanel.util.KiroFolderMonitor} (#74) and
 * {@link com.ourgiant.kirocontrolpanel.util.GitAutoCommitter}'s .gitignore
 * (#49) -- kept as this class's own copy of that list rather than shared,
 * matching how those two already each keep an independent copy. {@code .git}
 * (created by {@link com.ourgiant.kirocontrolpanel.util.GitAutoCommitter}, when
 * opted in) is excluded too, for the same reason {@code KiroFolderMonitor} never
 * watches it: it's that feature's own bookkeeping, not Kiro configuration content.
 */
public final class SnapshotService {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotService.class);

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of("sessions", "extensions", ".git");
    private static final String FILE_PREFIX = "kiro-snapshot-";
    private static final String FILE_SUFFIX = ".zip";
    // Sortable as a plain string, so listing snapshots newest-first never depends on
    // filesystem mtimes -- a destination folder synced via Dropbox/etc. can touch those.
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SnapshotService() {
    }

    /** Zips {@code kiroHome} into a new timestamped archive under {@code destinationDir}. */
    public static Path createSnapshot(Path kiroHome, Path destinationDir) throws IOException {
        Files.createDirectories(destinationDir);
        Path target = uniqueTargetPath(destinationDir, LocalDateTime.now());

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(target))) {
            Files.walkFileTree(kiroHome, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(kiroHome) && (isExcludedName(dir) || dir.equals(destinationDir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.equals(target)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entryName = kiroHome.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zos);
                    }
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }

        logger.info("Created .kiro snapshot at {}", target);
        return target;
    }

    /** Deletes all but the {@code keepCount} newest snapshots in {@code destinationDir}. */
    public static void pruneOldSnapshots(Path destinationDir, int keepCount) throws IOException {
        List<Path> snapshots = listSnapshotsNewestFirst(destinationDir);
        for (int i = keepCount; i < snapshots.size(); i++) {
            Path stale = snapshots.get(i);
            try {
                Files.delete(stale);
                logger.info("Pruned stale snapshot {}", stale);
            } catch (IOException e) {
                logger.warn("Failed to prune stale snapshot {}", stale, e);
            }
        }
    }

    /** The most recent snapshot's timestamp, parsed from its filename. */
    public static Optional<Instant> latestSnapshotTime(Path destinationDir) {
        List<Path> snapshots = listSnapshotsNewestFirst(destinationDir);
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        return parseTimestamp(snapshots.get(0));
    }

    private static List<Path> listSnapshotsNewestFirst(Path destinationDir) {
        List<Path> snapshots = new ArrayList<>();
        if (!Files.isDirectory(destinationDir)) {
            return snapshots;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(destinationDir,
                path -> isSnapshotFileName(String.valueOf(path.getFileName())))) {
            for (Path path : stream) {
                snapshots.add(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list snapshots in " + destinationDir, e);
        }
        snapshots.sort(Comparator.comparing((Path p) -> String.valueOf(p.getFileName())).reversed());
        return snapshots;
    }

    private static boolean isSnapshotFileName(String fileName) {
        return fileName.startsWith(FILE_PREFIX) && fileName.endsWith(FILE_SUFFIX);
    }

    private static Optional<Instant> parseTimestamp(Path snapshotFile) {
        String fileName = String.valueOf(snapshotFile.getFileName());
        String timestampPart = fileName.substring(FILE_PREFIX.length(),
            fileName.length() - FILE_SUFFIX.length());
        // Strip a "-N" disambiguation suffix appended by uniqueTargetPath, if present.
        int dashIndex = timestampPart.indexOf('-', "yyyyMMdd-HHmmss".length());
        if (dashIndex >= 0) {
            timestampPart = timestampPart.substring(0, dashIndex);
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(timestampPart, TIMESTAMP_FORMAT);
            return Optional.of(parsed.atZone(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException e) {
            logger.warn("Failed to parse snapshot timestamp from {}", fileName, e);
            return Optional.empty();
        }
    }

    private static Path uniqueTargetPath(Path destinationDir, LocalDateTime now) {
        String base = FILE_PREFIX + now.format(TIMESTAMP_FORMAT);
        Path candidate = destinationDir.resolve(base + FILE_SUFFIX);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = destinationDir.resolve(base + "-" + suffix + FILE_SUFFIX);
            suffix++;
        }
        return candidate;
    }

    private static boolean isExcludedName(Path path) {
        return EXCLUDED_DIR_NAMES.contains(String.valueOf(path.getFileName()));
    }
}

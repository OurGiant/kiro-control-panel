package com.ourgiant.kirocontrolpanel.changelog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSurface;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Append-only JSON-lines record of changes to Kiro-managed files -- both
 * self-writes (via this app) and externally-detected edits, across Global
 * and every pinned workspace. All-static, same shape as
 * {@link com.ourgiant.kirocontrolpanel.util.GitAutoCommitter}: an
 * explicit-log-file-path core (fully unit-testable) plus a real-path
 * convenience wrapper. See issue #79.
 * <p>
 * Deliberately a flat JSON-lines file, not a database -- this app already
 * decided against carrying a DB dependency for pure file-management
 * concerns (see SPEC.md), and Jackson (already a dependency) round-trips
 * this format with no custom serializers.
 */
public final class ChangeLogService {
    private static final Logger logger = LoggerFactory.getLogger(ChangeLogService.class);
    private static final Object LOCK = new Object();
    private static final ObjectMapper MAPPER = JsonMapperFactory.createMapper();

    private ChangeLogService() {
    }

    /** System property override for tests -- without it, every save/delete across this app's
     * whole test suite would otherwise append into the real user's real change log on every
     * {@code mvn test} run, since recording isn't gated behind an opt-in preference the way
     * {@code GitAutoCommitter} is. Set via {@code pom.xml}'s surefire config to a
     * build-directory-relative path, mirroring {@code KIRO_HOME}'s override precedent in
     * {@code KiroPaths} for the same class of problem. */
    private static final String LOG_FILE_OVERRIDE_PROPERTY = "kiro.control.panel.changeLogFile";

    public static Path defaultLogFile() {
        String override = System.getProperty(LOG_FILE_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".kiro-control-panel", "change-log.jsonl");
    }

    public static void record(Path filePath, ChangeKind kind, ChangeSource source) {
        record(defaultLogFile(), filePath, kind, source);
    }

    /**
     * Silently skips anything {@link KiroSurface#classify} doesn't recognize (e.g. a
     * {@code .gitignore} under {@code .kiro/}, or anything under {@code .git}) -- this is a
     * log of changes to Kiro-managed config, not a raw filesystem log. Best-effort: never
     * throws, matching {@code GitAutoCommitter.commitIfEnabled}'s convention that recording
     * a change never blocks the caller's own save.
     */
    public static void record(Path logFile, Path filePath, ChangeKind kind, ChangeSource source) {
        Optional<KiroSurface> surface = KiroSurface.classify(filePath);
        if (surface.isEmpty()) {
            return;
        }
        ScopeInfo scope = classifyScope(filePath);
        ChangeLogEntry entry = new ChangeLogEntry(
            Instant.now().toEpochMilli(),
            surface.get().name(),
            scope.label(),
            scope.global(),
            filePath.toAbsolutePath().normalize().toString(),
            kind.name(),
            source.name());
        synchronized (LOCK) {
            try {
                Files.createDirectories(logFile.getParent());
                String line = MAPPER.writeValueAsString(entry);
                Files.writeString(logFile, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.warn("Failed to record change log entry for {}", filePath, e);
            }
        }
    }

    public static List<ChangeLogEntry> loadSince(Instant cutoff) {
        return loadSince(defaultLogFile(), cutoff);
    }

    /** Unparseable lines are skipped (and logged), not fatal to the rest of the load. */
    public static List<ChangeLogEntry> loadSince(Path logFile, Instant cutoff) {
        List<ChangeLogEntry> entries = new ArrayList<>();
        if (!Files.isRegularFile(logFile)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(logFile)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    ChangeLogEntry entry = MAPPER.readValue(line, ChangeLogEntry.class);
                    if (!entry.timestamp().isBefore(cutoff)) {
                        entries.add(entry);
                    }
                } catch (IOException e) {
                    logger.warn("Skipping unparseable change log line", e);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to read change log {}", logFile, e);
        }
        entries.sort(Comparator.comparingLong(ChangeLogEntry::timestampEpochMilli).reversed());
        return entries;
    }

    public static void pruneOlderThan(Instant cutoff) {
        pruneOlderThan(defaultLogFile(), cutoff);
    }

    /** Rewrites the file keeping only entries at or after {@code cutoff}; unparseable lines are dropped. */
    public static void pruneOlderThan(Path logFile, Instant cutoff) {
        if (!Files.isRegularFile(logFile)) {
            return;
        }
        synchronized (LOCK) {
            try {
                List<String> kept = new ArrayList<>();
                for (String line : Files.readAllLines(logFile)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        ChangeLogEntry entry = MAPPER.readValue(line, ChangeLogEntry.class);
                        if (!entry.timestamp().isBefore(cutoff)) {
                            kept.add(line);
                        }
                    } catch (IOException e) {
                        logger.warn("Dropping unparseable change log line during prune", e);
                    }
                }
                Files.write(logFile, kept, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                logger.warn("Failed to prune change log {}", logFile, e);
            }
        }
    }

    private record ScopeInfo(String label, boolean global) {
    }

    private static ScopeInfo classifyScope(Path filePath) {
        Path normalized = filePath.toAbsolutePath().normalize();
        Path globalHome = KiroPaths.globalKiroHome().toAbsolutePath().normalize();
        if (normalized.startsWith(globalHome)) {
            return new ScopeInfo("Global", true);
        }
        Path dir = normalized.getParent();
        while (dir != null) {
            if (".kiro".equals(String.valueOf(dir.getFileName()))) {
                Path workspaceRoot = dir.getParent();
                return new ScopeInfo(workspaceRoot != null ? workspaceRoot.toString() : "Unknown", false);
            }
            dir = dir.getParent();
        }
        return new ScopeInfo("Unknown", false);
    }
}

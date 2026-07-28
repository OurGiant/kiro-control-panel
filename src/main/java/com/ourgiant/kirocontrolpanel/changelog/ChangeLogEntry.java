package com.ourgiant.kirocontrolpanel.changelog;

import com.ourgiant.kirocontrolpanel.diagnostics.KiroSurface;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * One JSON-line record in the change log. Plain Jackson-trivial field types
 * (long/String/boolean) so it round-trips through {@code ObjectMapper} with
 * no custom (de)serializers -- {@code Instant}/{@code Path}/enum values are
 * exposed via derived accessor methods instead of being stored directly.
 */
public record ChangeLogEntry(
    long timestampEpochMilli,
    String surface,
    String scopeLabel,
    boolean global,
    String filePath,
    String kind,
    String source
) {
    public Instant timestamp() {
        return Instant.ofEpochMilli(timestampEpochMilli);
    }

    public Path path() {
        return Paths.get(filePath);
    }

    public KiroSurface surfaceEnum() {
        return KiroSurface.valueOf(surface);
    }

    public ChangeKind kindEnum() {
        return ChangeKind.valueOf(kind);
    }

    public ChangeSource sourceEnum() {
        return ChangeSource.valueOf(source);
    }
}

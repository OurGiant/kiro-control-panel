package com.ourgiant.kirocontrolpanel.sessions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

/**
 * One kiro-cli session's manifest: everything the Sessions tab's table/detail
 * pane needs, derived from a {@code <uuid>.json} sidecar plus its paired
 * {@code <uuid>.jsonl} transcript. Plain Jackson-trivial field types so it
 * round-trips through {@code ObjectMapper} with no custom (de)serializers --
 * same convention as {@code changelog.ChangeLogEntry} -- with {@code Instant}/
 * {@code Path} exposed via derived accessor methods.
 */
public record SessionManifest(
    String sessionId,
    String cwd,
    long createdAtEpochMilli,
    long updatedAtEpochMilli,
    String title,
    String sessionCreatedReason,
    int messageCount,
    List<String> filesTouched,
    List<String> reposTouched,
    String sourceJsonPath,
    String sourceJsonlPath
) {
    public Instant createdAt() {
        return Instant.ofEpochMilli(createdAtEpochMilli);
    }

    public Instant updatedAt() {
        return Instant.ofEpochMilli(updatedAtEpochMilli);
    }

    public Path sourceJson() {
        return Paths.get(sourceJsonPath);
    }

    public Path sourceJsonl() {
        return Paths.get(sourceJsonlPath);
    }

    public int filesTouchedCount() {
        return filesTouched.size();
    }
}

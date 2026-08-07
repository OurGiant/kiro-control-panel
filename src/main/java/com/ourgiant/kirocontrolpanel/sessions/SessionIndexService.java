package com.ourgiant.kirocontrolpanel.sessions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the Sessions tab's SQLite/FTS5 index: one JDBC connection, synchronized
 * writes (SQLite is single-writer, same reasoning as
 * {@code changelog.ChangeLogService}'s {@code LOCK}), incremental per-session
 * reindexing keyed off stored file mtimes and a resumable transcript line
 * offset -- an append-only {@code .jsonl} is never re-read from the start.
 * See {@link SessionManifestParser} for the parsing this builds on and
 * {@link SessionIndexSchema} for the DDL.
 */
public final class SessionIndexService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SessionIndexService.class);

    private final Object lock = new Object();
    private final Connection connection;
    private volatile Runnable updateListener;

    public SessionIndexService(Path dbFile) throws SQLException, IOException {
        Path absolute = dbFile.toAbsolutePath().normalize();
        if (absolute.getParent() != null) {
            Files.createDirectories(absolute.getParent());
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        try (Statement statement = connection.createStatement()) {
            // WAL lets the EDT read (listAll/search) while a background scan writes.
            statement.execute("PRAGMA journal_mode=WAL");
        }
        SessionIndexSchema.create(connection);
    }

    /** Invoked (not marshaled to the EDT -- callers do that) after a commit that may have
     * changed what {@link #listAll()} would return. */
    public void setUpdateListener(Runnable listener) {
        this.updateListener = listener;
    }

    /**
     * Re-indexes a single session (its sidecar always, its transcript incrementally from
     * whatever line was previously indexed) and notifies the update listener once. Used by
     * the live folder-watcher callback for one changed file at a time.
     */
    public void indexOne(Path jsonSidecar) throws IOException, SQLException {
        synchronized (lock) {
            indexOneLocked(jsonSidecar);
        }
        notifyListener();
    }

    /**
     * Walks every {@code <uuid>.json} sidecar under {@code sessionsCliDir}, re-indexing only
     * those whose sidecar or transcript file has changed since it was last indexed, then prunes
     * rows for sessions whose files no longer exist. Notifies the update listener once at the
     * end, not per-file, so a large first-run scan doesn't thrash the UI.
     */
    public void scanAndIndex(Path sessionsCliDir) throws IOException, SQLException {
        if (!Files.isDirectory(sessionsCliDir)) {
            return;
        }
        Set<String> presentSessionIds = new HashSet<>();
        synchronized (lock) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsCliDir, "*.json")) {
                for (Path jsonSidecar : stream) {
                    String sessionId = stemOf(jsonSidecar);
                    presentSessionIds.add(sessionId);
                    try {
                        if (needsReindexLocked(sessionId, jsonSidecar)) {
                            indexOneLocked(jsonSidecar);
                        }
                    } catch (IOException | SQLException e) {
                        logger.warn("Failed to index session file {}", jsonSidecar, e);
                    }
                }
            }
            pruneMissingLocked(presentSessionIds);
        }
        notifyListener();
    }

    /** Drops and fully rebuilds the index from {@code sessionsCliDir} -- the Settings dialog's
     * "Rebuild Index" action. */
    public void rebuildIndex(Path sessionsCliDir) throws IOException, SQLException {
        synchronized (lock) {
            SessionIndexSchema.drop(connection);
            SessionIndexSchema.create(connection);
        }
        scanAndIndex(sessionsCliDir);
    }

    public List<SessionManifest> listAll() throws SQLException {
        List<SessionManifest> manifests = new ArrayList<>();
        synchronized (lock) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                     "SELECT session_id, cwd, created_at_millis, updated_at_millis, title, "
                         + "session_created_reason, message_count, source_json_path, source_jsonl_path "
                         + "FROM sessions ORDER BY created_at_millis DESC")) {
                while (rs.next()) {
                    String sessionId = rs.getString("session_id");
                    manifests.add(new SessionManifest(
                        sessionId,
                        rs.getString("cwd"),
                        rs.getLong("created_at_millis"),
                        rs.getLong("updated_at_millis"),
                        rs.getString("title"),
                        rs.getString("session_created_reason"),
                        rs.getInt("message_count"),
                        queryChildValuesLocked("session_files", "path", sessionId),
                        queryChildValuesLocked("session_repos", "repo_path", sessionId),
                        rs.getString("source_json_path"),
                        rs.getString("source_jsonl_path")));
                }
            }
        }
        return manifests;
    }

    public List<String> filesTouchedFor(String sessionId) throws SQLException {
        synchronized (lock) {
            return queryChildValuesLocked("session_files", "path", sessionId);
        }
    }

    /** Ranked FTS5 match over indexed message text (never tool payloads -- see
     * {@link SessionManifestParser}). Lower {@code bm25} is a better match, so results come
     * back best-first without needing to reverse the ordering. */
    public List<SessionSearchResult> search(String query, int limit) throws SQLException {
        List<SessionSearchResult> results = new ArrayList<>();
        String sql = "SELECT session_id, role, snippet(session_messages_fts, 2, '[', ']', '...', 8) AS snip, "
            + "bm25(session_messages_fts) AS rank FROM session_messages_fts "
            + "WHERE session_messages_fts MATCH ? ORDER BY rank LIMIT ?";
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, query);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new SessionSearchResult(
                            rs.getString("session_id"),
                            rs.getString("role"),
                            rs.getString("snip"),
                            rs.getDouble("rank")));
                    }
                }
            }
        }
        return results;
    }

    /** Removes a session's rows (manifest, files, repos, FTS messages) -- used by the live
     * watcher when a session's files disappear between scans. */
    public void removeSession(String sessionId) throws SQLException {
        synchronized (lock) {
            deleteSessionLocked(sessionId);
        }
        notifyListener();
    }

    @Override
    public void close() throws SQLException {
        synchronized (lock) {
            connection.close();
        }
    }

    // -- internals; the *Locked methods assume `lock` is already held --

    private void indexOneLocked(Path jsonSidecar) throws IOException, SQLException {
        String sessionId = stemOf(jsonSidecar);
        Path jsonl = jsonSidecar.resolveSibling(sessionId + ".jsonl");

        SessionSidecar sidecar = SessionManifestParser.parseSidecar(jsonSidecar);
        long jsonMtime = Files.getLastModifiedTime(jsonSidecar).toMillis();

        Bookkeeping existing = queryBookkeepingLocked(sessionId);
        long fromLine = existing != null ? existing.jsonlIndexedLines() : 0L;
        TranscriptStats delta = SessionManifestParser.parseTranscript(jsonl, fromLine);
        long jsonlMtime = Files.isRegularFile(jsonl) ? Files.getLastModifiedTime(jsonl).toMillis() : 0L;

        String title = sidecar.title() != null && !sidecar.title().isBlank() ? sidecar.title() : delta.firstPromptText();
        if (title == null && existing != null) {
            title = existing.title();
        }
        int messageCount = (existing != null ? existing.messageCount() : 0) + delta.messageCount();

        upsertSessionLocked(sessionId, sidecar, jsonSidecar, jsonl, title, messageCount, jsonMtime, jsonlMtime,
            delta.linesConsumed());
        insertChildRowsLocked("session_files", "path", sessionId, delta.filesTouched());
        insertChildRowsLocked("session_repos", "repo_path", sessionId, delta.reposTouched());
        insertMessagesLocked(sessionId, delta.messages());
    }

    private record Bookkeeping(long jsonMtimeMillis, long jsonlMtimeMillis, long jsonlIndexedLines,
                                int messageCount, String title) {
    }

    private Bookkeeping queryBookkeepingLocked(String sessionId) throws SQLException {
        String sql = "SELECT json_mtime_millis, jsonl_mtime_millis, jsonl_indexed_lines, message_count, title "
            + "FROM sessions WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Bookkeeping(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getString(5));
            }
        }
    }

    private boolean needsReindexLocked(String sessionId, Path jsonSidecar) throws IOException, SQLException {
        Bookkeeping existing = queryBookkeepingLocked(sessionId);
        if (existing == null) {
            return true;
        }
        long jsonMtime = Files.getLastModifiedTime(jsonSidecar).toMillis();
        if (jsonMtime != existing.jsonMtimeMillis()) {
            return true;
        }
        Path jsonl = jsonSidecar.resolveSibling(sessionId + ".jsonl");
        long jsonlMtime = Files.isRegularFile(jsonl) ? Files.getLastModifiedTime(jsonl).toMillis() : 0L;
        return jsonlMtime != existing.jsonlMtimeMillis();
    }

    private void upsertSessionLocked(String sessionId, SessionSidecar sidecar, Path jsonSidecar, Path jsonl,
                                      String title, int messageCount, long jsonMtime, long jsonlMtime,
                                      long jsonlIndexedLines) throws SQLException {
        String sql = """
            INSERT INTO sessions (session_id, cwd, created_at_millis, updated_at_millis, title,
                session_created_reason, message_count, source_json_path, source_jsonl_path,
                json_mtime_millis, jsonl_mtime_millis, jsonl_indexed_lines)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
                cwd = excluded.cwd,
                created_at_millis = excluded.created_at_millis,
                updated_at_millis = excluded.updated_at_millis,
                title = excluded.title,
                session_created_reason = excluded.session_created_reason,
                message_count = excluded.message_count,
                source_json_path = excluded.source_json_path,
                source_jsonl_path = excluded.source_jsonl_path,
                json_mtime_millis = excluded.json_mtime_millis,
                jsonl_mtime_millis = excluded.jsonl_mtime_millis,
                jsonl_indexed_lines = excluded.jsonl_indexed_lines
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, sidecar.cwd());
            ps.setLong(3, sidecar.createdAtEpochMilli());
            ps.setLong(4, sidecar.updatedAtEpochMilli());
            ps.setString(5, title);
            ps.setString(6, sidecar.sessionCreatedReason());
            ps.setInt(7, messageCount);
            ps.setString(8, jsonSidecar.toAbsolutePath().normalize().toString());
            ps.setString(9, jsonl.toAbsolutePath().normalize().toString());
            ps.setLong(10, jsonMtime);
            ps.setLong(11, jsonlMtime);
            ps.setLong(12, jsonlIndexedLines);
            ps.executeUpdate();
        }
    }

    private void insertChildRowsLocked(String table, String column, String sessionId, List<String> values)
        throws SQLException {
        if (values.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + table + " (session_id, " + column + ") VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (String value : values) {
                ps.setString(1, sessionId);
                ps.setString(2, value);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertMessagesLocked(String sessionId, List<TranscriptMessage> messages) throws SQLException {
        if (messages.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO session_messages_fts (session_id, role, content, message_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (TranscriptMessage message : messages) {
                ps.setString(1, sessionId);
                ps.setString(2, message.role());
                ps.setString(3, message.text());
                ps.setString(4, message.messageId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<String> queryChildValuesLocked(String table, String column, String sessionId) throws SQLException {
        String sql = "SELECT " + column + " FROM " + table + " WHERE session_id = ?";
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
        }
        return values;
    }

    private void pruneMissingLocked(Set<String> presentSessionIds) throws SQLException {
        List<String> toRemove = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT session_id FROM sessions")) {
            while (rs.next()) {
                String sessionId = rs.getString(1);
                if (!presentSessionIds.contains(sessionId)) {
                    toRemove.add(sessionId);
                }
            }
        }
        for (String sessionId : toRemove) {
            deleteSessionLocked(sessionId);
        }
    }

    private void deleteSessionLocked(String sessionId) throws SQLException {
        for (String sql : List.of(
            "DELETE FROM sessions WHERE session_id = ?",
            "DELETE FROM session_files WHERE session_id = ?",
            "DELETE FROM session_repos WHERE session_id = ?",
            "DELETE FROM session_messages_fts WHERE session_id = ?")) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
        }
    }

    private static String stemOf(Path jsonSidecar) {
        String name = jsonSidecar.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    private void notifyListener() {
        Runnable listener = updateListener;
        if (listener != null) {
            listener.run();
        }
    }
}

package com.ourgiant.kirocontrolpanel.sessions;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DDL for the Sessions tab's SQLite index (issue #117): a manifest table,
 * two small child tables for the files/repos a session touched, and an
 * FTS5 virtual table over user/assistant message text. All statements are
 * idempotent ({@code IF NOT EXISTS}/{@code IF EXISTS}) so {@link #create}
 * and {@link #drop} are safe to call against an already-initialized or
 * already-empty database.
 */
final class SessionIndexSchema {

    private static final String CREATE_SESSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS sessions (
            session_id TEXT PRIMARY KEY,
            cwd TEXT NOT NULL DEFAULT '',
            created_at_millis INTEGER NOT NULL DEFAULT 0,
            updated_at_millis INTEGER NOT NULL DEFAULT 0,
            title TEXT,
            session_created_reason TEXT NOT NULL DEFAULT '',
            message_count INTEGER NOT NULL DEFAULT 0,
            source_json_path TEXT NOT NULL,
            source_jsonl_path TEXT NOT NULL,
            json_mtime_millis INTEGER NOT NULL DEFAULT 0,
            jsonl_mtime_millis INTEGER NOT NULL DEFAULT 0,
            jsonl_indexed_lines INTEGER NOT NULL DEFAULT 0
        )""";

    private static final String CREATE_SESSION_FILES_TABLE = """
        CREATE TABLE IF NOT EXISTS session_files (
            session_id TEXT NOT NULL,
            path TEXT NOT NULL
        )""";

    private static final String CREATE_SESSION_FILES_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_session_files_session_id ON session_files(session_id)";

    private static final String CREATE_SESSION_REPOS_TABLE = """
        CREATE TABLE IF NOT EXISTS session_repos (
            session_id TEXT NOT NULL,
            repo_path TEXT NOT NULL
        )""";

    private static final String CREATE_SESSION_REPOS_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_session_repos_session_id ON session_repos(session_id)";

    /** Message text only -- never tool call/result payloads. See SessionManifestParser. */
    private static final String CREATE_MESSAGES_FTS_TABLE = """
        CREATE VIRTUAL TABLE IF NOT EXISTS session_messages_fts USING fts5(
            session_id UNINDEXED,
            role UNINDEXED,
            content,
            message_id UNINDEXED
        )""";

    private SessionIndexSchema() {
    }

    static void create(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_SESSIONS_TABLE);
            statement.execute(CREATE_SESSION_FILES_TABLE);
            statement.execute(CREATE_SESSION_FILES_INDEX);
            statement.execute(CREATE_SESSION_REPOS_TABLE);
            statement.execute(CREATE_SESSION_REPOS_INDEX);
            statement.execute(CREATE_MESSAGES_FTS_TABLE);
        }
    }

    /** Used by "Rebuild Index" -- drops everything so {@link #create} starts from empty. */
    static void drop(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS session_messages_fts");
            statement.execute("DROP TABLE IF EXISTS session_repos");
            statement.execute("DROP TABLE IF EXISTS session_files");
            statement.execute("DROP TABLE IF EXISTS sessions");
        }
    }
}

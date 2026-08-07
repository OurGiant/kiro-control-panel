package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures below are hand-written, structurally shaped like real kiro-cli
 * 2.13.0 session data (confirmed against real seeded sessions for issue
 * #117) but with fabricated content -- never copied real transcript text.
 */
class SessionManifestParserTest {

    @TempDir
    Path tempDir;

    private Path sidecar(String title) throws IOException {
        Path file = tempDir.resolve("session.json");
        String titleJson = title == null ? "null" : "\"" + title + "\"";
        Files.writeString(file, """
            {
              "session_id": "72897549-046d-4d9f-853c-df9896b9a4df",
              "cwd": "/home/example/projects/demo",
              "created_at": "2026-08-07T11:26:45.323381992Z",
              "updated_at": "2026-08-07T11:29:38.111897334Z",
              "title": %s,
              "session_created_reason": "interactive"
            }
            """.formatted(titleJson));
        return file;
    }

    private Path jsonlOf(String... lines) throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, String.join("\n", lines) + "\n");
        return file;
    }

    private static final String PROMPT_LINE = """
        {"version":"v1","kind":"Prompt","data":{"message_id":"m1","content":[{"kind":"text","data":"read the code and tell me what you think"}],"meta":{"timestamp":1}}}""";
    private static final String ASSISTANT_WITH_TOOL_USE_LINE = """
        {"version":"v1","kind":"AssistantMessage","data":{"message_id":"m2","content":[{"kind":"text","data":""},{"kind":"toolUse","data":{"toolUseId":"t1","name":"read","input":{"operations":[{"mode":"Directory","path":"/home/example/projects/demo","depth":1}]}}}]}}""";
    private static final String TOOL_RESULTS_FILE_READ_LINE = """
        {"version":"v1","kind":"ToolResults","data":{"message_id":"m3","content":[{"kind":"toolResult","data":{"toolUseId":"t1","content":[{"kind":"text","data":"listing"}],"status":"Success"}}],"results":{"t1":{"tool":{"tool_use_purpose":"list dir","kind":{"BuiltIn":{"FileRead":{"operations":[{"mode":"Directory","path":"/home/example/projects/demo","depth":1,"exclude_patterns":null}]}}}},"result":{"Success":{"items":[{"Text":"listing"}]}}}}}}""";
    private static final String ASSISTANT_FINAL_LINE = """
        {"version":"v1","kind":"AssistantMessage","data":{"message_id":"m4","content":[{"kind":"text","data":"done"}]}}""";

    @Test
    void buildManifestUsesSidecarTitleWhenPresent() throws IOException {
        Path sidecar = sidecar("read the code and tell me what you think");
        Path jsonl = jsonlOf(PROMPT_LINE, ASSISTANT_WITH_TOOL_USE_LINE, TOOL_RESULTS_FILE_READ_LINE, ASSISTANT_FINAL_LINE);

        SessionManifest manifest = SessionManifestParser.buildManifest(sidecar, jsonl);

        assertEquals("72897549-046d-4d9f-853c-df9896b9a4df", manifest.sessionId());
        assertEquals("/home/example/projects/demo", manifest.cwd());
        assertEquals("read the code and tell me what you think", manifest.title());
        assertEquals("interactive", manifest.sessionCreatedReason());
        assertEquals(3, manifest.messageCount()); // Prompt + 2 AssistantMessage, not ToolResults
        assertTrue(manifest.filesTouched().isEmpty()); // FileRead is a read, not a "touch"
        assertTrue(manifest.reposTouched().isEmpty());
        assertTrue(manifest.sourceJsonPath().endsWith("session.json"));
        assertTrue(manifest.sourceJsonlPath().endsWith("session.jsonl"));
    }

    @Test
    void buildManifestFallsBackToFirstPromptWhenSidecarTitleIsNull() throws IOException {
        Path sidecar = sidecar(null);
        Path jsonl = jsonlOf(PROMPT_LINE, ASSISTANT_FINAL_LINE);

        SessionManifest manifest = SessionManifestParser.buildManifest(sidecar, jsonl);

        assertEquals("read the code and tell me what you think", manifest.title());
    }

    @Test
    void buildManifestTitleIsNullWhenNeitherSidecarNorTranscriptHaveOne() throws IOException {
        Path sidecar = sidecar(null);
        Path jsonl = jsonlOf(ASSISTANT_FINAL_LINE);

        SessionManifest manifest = SessionManifestParser.buildManifest(sidecar, jsonl);

        assertNull(manifest.title());
    }

    @Test
    void parseTranscriptSkipsUnparseableLinesWithoutFailingTheWholeParse() throws IOException {
        Path jsonl = jsonlOf(PROMPT_LINE, "{ not valid json", ASSISTANT_FINAL_LINE);

        TranscriptStats stats = SessionManifestParser.parseTranscript(jsonl, 0);

        assertEquals(2, stats.messageCount());
        assertEquals(3, stats.linesConsumed());
    }

    @Test
    void parseTranscriptCollectsUserAndAssistantTextMessagesForFullTextIndexingButNotToolPayloads() throws IOException {
        Path jsonl = jsonlOf(PROMPT_LINE, ASSISTANT_WITH_TOOL_USE_LINE, TOOL_RESULTS_FILE_READ_LINE, ASSISTANT_FINAL_LINE);

        TranscriptStats stats = SessionManifestParser.parseTranscript(jsonl, 0);

        // Prompt + the final AssistantMessage have real text; the toolUse-only AssistantMessage
        // (empty "text" content entry) contributes no message, and ToolResults contributes none.
        assertEquals(2, stats.messages().size());
        assertEquals("user", stats.messages().get(0).role());
        assertEquals("read the code and tell me what you think", stats.messages().get(0).text());
        assertEquals("assistant", stats.messages().get(1).role());
        assertEquals("done", stats.messages().get(1).text());
    }

    @Test
    void unrecognizedBuiltInToolKindIsSkippedRatherThanCrashingOrGuessing() throws IOException {
        String toolResultsWithUnconfirmedWriteKind = """
            {"version":"v1","kind":"ToolResults","data":{"message_id":"m3","content":[],"results":{"t1":{"tool":{"tool_use_purpose":"write a file","kind":{"BuiltIn":{"FileWrite":{"path":"/home/example/projects/demo/out.txt"}}}},"result":{"Success":{"items":[]}}}}}}""";
        Path jsonl = jsonlOf(PROMPT_LINE, toolResultsWithUnconfirmedWriteKind, ASSISTANT_FINAL_LINE);

        TranscriptStats stats = SessionManifestParser.parseTranscript(jsonl, 0);

        assertEquals(2, stats.messageCount());
        assertTrue(stats.filesTouched().isEmpty()); // FileWrite's real shape is unconfirmed -- not guessed
    }

    @Test
    void parseTranscriptResumesFromAGivenLineForIncrementalReindexing() throws IOException {
        Path jsonl = jsonlOf(PROMPT_LINE, ASSISTANT_WITH_TOOL_USE_LINE, TOOL_RESULTS_FILE_READ_LINE, ASSISTANT_FINAL_LINE);
        long totalLines = SessionManifestParser.parseTranscript(jsonl, 0).linesConsumed();

        TranscriptStats delta = SessionManifestParser.parseTranscript(jsonl, totalLines - 1);

        assertEquals(1, delta.messageCount()); // only the final AssistantMessage line
        assertEquals(totalLines, delta.linesConsumed());
    }

    @Test
    void parseTranscriptOfMissingFileProducesEmptyStats() throws IOException {
        Path missing = tempDir.resolve("does-not-exist.jsonl");

        TranscriptStats stats = SessionManifestParser.parseTranscript(missing, 0);

        assertEquals(0, stats.messageCount());
        assertEquals(0, stats.linesConsumed());
        assertTrue(stats.filesTouched().isEmpty());
    }

    @Test
    void parseSidecarToleratesUnknownFieldsLikeSessionState() throws IOException {
        Path file = tempDir.resolve("session.json");
        Files.writeString(file, """
            {
              "session_id": "abc",
              "cwd": "/tmp",
              "created_at": "2026-08-07T11:26:45.323381992Z",
              "updated_at": "2026-08-07T11:29:38.111897334Z",
              "title": null,
              "session_created_reason": "subagent",
              "session_state": {"version": "v1", "agent_name": "kiro_default", "goal": null}
            }
            """);

        SessionSidecar sidecar = SessionManifestParser.parseSidecar(file);

        assertEquals("abc", sidecar.sessionId());
        assertEquals("subagent", sidecar.sessionCreatedReason());
    }
}

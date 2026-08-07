package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionIndexServiceTest {

    @TempDir
    Path tempDir;

    private Path dbFile() {
        return tempDir.resolve("sessions-index.db");
    }

    private Path sessionsDir() throws IOException {
        Path dir = tempDir.resolve("sessions-cli");
        Files.createDirectories(dir);
        return dir;
    }

    private void writeSession(Path dir, String sessionId, String title, String... jsonlLines) throws IOException {
        String titleJson = title == null ? "null" : "\"" + title + "\"";
        Files.writeString(dir.resolve(sessionId + ".json"), """
            {
              "session_id": "%s",
              "cwd": "/home/example/projects/demo",
              "created_at": "2026-08-07T11:26:45.323381992Z",
              "updated_at": "2026-08-07T11:29:38.111897334Z",
              "title": %s,
              "session_created_reason": "interactive"
            }
            """.formatted(sessionId, titleJson));
        String content = jsonlLines.length == 0 ? "" : String.join("\n", jsonlLines) + "\n";
        Files.writeString(dir.resolve(sessionId + ".jsonl"), content);
    }

    private static String promptLine(String text) {
        return """
            {"version":"v1","kind":"Prompt","data":{"message_id":"m1","content":[{"kind":"text","data":"%s"}],"meta":{"timestamp":1}}}""".formatted(text);
    }

    private static String assistantLine(String text) {
        return """
            {"version":"v1","kind":"AssistantMessage","data":{"message_id":"m2","content":[{"kind":"text","data":"%s"}]}}""".formatted(text);
    }

    @Test
    void constructingTwiceAgainstTheSameFileIsIdempotent() throws Exception {
        new SessionIndexService(dbFile()).close();
        SessionIndexService second = new SessionIndexService(dbFile());
        second.close();
    }

    @Test
    void scanAndIndexPopulatesManifestFieldsFromSidecarAndTranscript() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-a", "what should I change and why",
            promptLine("what should I change and why"), assistantLine("done"));

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.scanAndIndex(dir);

            List<SessionManifest> all = service.listAll();
            assertEquals(1, all.size());
            SessionManifest manifest = all.get(0);
            assertEquals("session-a", manifest.sessionId());
            assertEquals("/home/example/projects/demo", manifest.cwd());
            assertEquals("what should I change and why", manifest.title());
            assertEquals(2, manifest.messageCount());
        }
    }

    @Test
    void indexOneIsIncrementalWhenCalledAgainAfterLinesAreAppended() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-b", "first prompt", promptLine("first prompt"));
        Path jsonSidecar = dir.resolve("session-b.json");

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.indexOne(jsonSidecar);
            assertEquals(1, service.listAll().get(0).messageCount());

            // kiro-cli's transcript is append-only; simulate a second turn being written.
            Files.writeString(dir.resolve("session-b.jsonl"), assistantLine("second message") + "\n",
                StandardOpenOption.APPEND);

            service.indexOne(jsonSidecar);

            assertEquals(2, service.listAll().get(0).messageCount());
        }
    }

    @Test
    void scanAndIndexSkipsFilesWhoseMtimesHaveNotChanged() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-c", "hello", promptLine("hello"));

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.scanAndIndex(dir);
            service.scanAndIndex(dir); // second pass: nothing changed, must not double-count

            assertEquals(1, service.listAll().get(0).messageCount());
        }
    }

    @Test
    void scanAndIndexPrunesSessionsWhoseFilesWereDeleted() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-d", "hello", promptLine("hello"));

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.scanAndIndex(dir);
            assertEquals(1, service.listAll().size());

            Files.delete(dir.resolve("session-d.json"));
            Files.delete(dir.resolve("session-d.jsonl"));
            service.scanAndIndex(dir);

            assertTrue(service.listAll().isEmpty());
        }
    }

    @Test
    void searchReturnsRankedMatchesFromIndexedMessageTextOnly() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-e", null,
            promptLine("please refactor the sessions package"), assistantLine("sure, refactoring now"));

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.scanAndIndex(dir);

            List<SessionSearchResult> results = service.search("refactor*", 10);

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> "session-e".equals(r.sessionId())));
        }
    }

    @Test
    void rebuildIndexDropsAndFullyReindexes() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-f", "hello", promptLine("hello"));

        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.scanAndIndex(dir);
            assertEquals(1, service.listAll().size());

            service.rebuildIndex(dir);

            assertEquals(1, service.listAll().size());
            assertEquals(1, service.search("hello", 10).size());
        }
    }

    @Test
    void updateListenerFiresOnceAfterScanAndIndex() throws Exception {
        Path dir = sessionsDir();
        writeSession(dir, "session-g", "hello", promptLine("hello"));

        int[] callCount = {0};
        try (SessionIndexService service = new SessionIndexService(dbFile())) {
            service.setUpdateListener(() -> callCount[0]++);
            service.scanAndIndex(dir);
        }

        assertEquals(1, callCount[0]);
    }
}

package com.ourgiant.kirocontrolpanel.sessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionsWatcherTest {

    @TempDir
    Path tempDir;

    /**
     * Regression test for the "must root at sessions/cli, not sessions" gotcha documented on
     * {@code KiroPaths.globalSessionsCliDir()}: {@link com.ourgiant.kirocontrolpanel.util.KiroFolderMonitor}
     * skips any directory literally named {@code sessions} -- including the watch root itself,
     * confirmed by reading that class before relying on it -- so rooting one level too high
     * would silently register nothing. This proves the watcher, rooted at a {@code .../sessions/cli}
     * path, actually receives and indexes a real change.
     */
    @Test
    @Timeout(30)
    void rootingAtSessionsCliActuallyReceivesChangeEvents() throws Exception {
        Path sessionsDir = tempDir.resolve("sessions");
        Path cliDir = sessionsDir.resolve("cli");
        Files.createDirectories(cliDir);

        Path dbFile = tempDir.resolve("index.db");
        try (SessionIndexService indexService = new SessionIndexService(dbFile)) {
            CountDownLatch latch = new CountDownLatch(1);
            indexService.setUpdateListener(latch::countDown);

            SessionsWatcher watcher = new SessionsWatcher(cliDir, indexService);
            watcher.start();
            try {
                writeSession(cliDir, "session-x");

                assertTrue(latch.await(20, TimeUnit.SECONDS), "expected the watcher to notice the new session");
                assertEquals(1, indexService.listAll().size());
                assertEquals("session-x", indexService.listAll().get(0).sessionId());
            } finally {
                watcher.close();
            }
        }
    }

    private static void writeSession(Path dir, String sessionId) throws Exception {
        Files.writeString(dir.resolve(sessionId + ".json"), """
            {
              "session_id": "%s",
              "cwd": "/tmp",
              "created_at": "2026-08-07T11:26:45.323381992Z",
              "updated_at": "2026-08-07T11:29:38.111897334Z",
              "title": "hello",
              "session_created_reason": "interactive"
            }
            """.formatted(sessionId));
        Files.writeString(dir.resolve(sessionId + ".jsonl"), """
            {"version":"v1","kind":"Prompt","data":{"message_id":"m1","content":[{"kind":"text","data":"hello"}],"meta":{"timestamp":1}}}
            """);
    }
}

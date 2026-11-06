package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sessions tab reading kiro-cli V3's session store next to V2's (issue #147). */
class SessionsV3Test {
    private static final String V3_ID = "sess_2bf6c291-6c4c-48da-8b91-8210a536a0ee";
    private static final String WORKSPACE = "/home/example/aws-idp-saml-ui";

    @TempDir
    Path tempDir;

    @Test
    void engineIsTakenFromTheFileLayout() throws Exception {
        Path sessionJson = V3SessionFixtures.writeV3Session(tempDir, V3_ID, WORKSPACE, "Project overview");
        assertEquals(SessionEngine.V3, SessionEngine.ofSidecar(sessionJson));
        assertEquals(V3_ID, SessionEngine.V3.sessionIdFor(sessionJson));
        assertEquals(sessionJson.resolveSibling("messages.jsonl"), SessionEngine.V3.transcriptFor(sessionJson));

        Path v2 = tempDir.resolve("cli").resolve("72897549-046d-4d9f-853c-df9896b9a4df.json");
        assertEquals(SessionEngine.V2, SessionEngine.ofSidecar(v2));
        assertEquals("72897549-046d-4d9f-853c-df9896b9a4df", SessionEngine.V2.sessionIdFor(v2));
        // A file merely named session.json outside a sess_* folder is not a V3 session.
        assertEquals(SessionEngine.V2, SessionEngine.ofSidecar(tempDir.resolve("other").resolve("session.json")));
    }

    @Test
    void v3ManifestHasTitleWorkspaceMessagesAndWrittenFilesButNoToolOrSystemText() throws Exception {
        Path sessionJson = V3SessionFixtures.writeV3Session(tempDir, V3_ID, WORKSPACE, "Project overview");
        SessionManifest manifest = SessionManifestParser.buildManifest(sessionJson,
            SessionEngine.V3.transcriptFor(sessionJson));

        assertEquals(V3_ID, manifest.sessionId());
        assertEquals(WORKSPACE, manifest.cwd());
        assertEquals("Project overview", manifest.title());
        assertEquals(2, manifest.messageCount());
        assertEquals(SessionEngine.V3, manifest.engine());
        // fs_write by name, strReplace by ACP kind "edit"; the readFile call is not a write.
        // The relative path is resolved against the session's workspace.
        // Built with Path so the expectation matches each OS's own separators (Windows CI).
        Path workspace = Path.of(WORKSPACE);
        assertEquals(List.of(workspace.resolve("README.md").toString(),
            workspace.resolve("/abs/Other.java").normalize().toString()), manifest.filesTouched());

        TranscriptStats stats = SessionManifestParser.parseTranscript(SessionEngine.V3.transcriptFor(sessionJson), 0);
        assertEquals(2, stats.messages().size());
        assertEquals("user", stats.messages().get(0).role());
        assertEquals("u1", stats.messages().get(0).messageId());
        assertTrue(stats.messages().stream().noneMatch(m -> m.text().contains("must not be indexed")));
    }

    @Test
    void v3PlaceholderTitleFallsBackToTheOpeningPrompt() throws Exception {
        Path sessionJson = V3SessionFixtures.writeV3Session(tempDir, V3_ID, WORKSPACE, "New Session");
        SessionManifest manifest = SessionManifestParser.buildManifest(sessionJson,
            SessionEngine.V3.transcriptFor(sessionJson));
        assertEquals("what can you tell me about this project?", manifest.title());
    }

    @Test
    void v3UsageSumsCreditsPerTurnAndKeepsTheLatestContextPercentage() throws Exception {
        Path sessionJson = V3SessionFixtures.writeV3Session(tempDir, V3_ID, WORKSPACE, "Project overview");
        SessionUsage usage = SessionManifestParser.parseUsage(sessionJson);
        assertEquals(1, usage.turnCount());
        assertEquals(1.25, usage.totalCredits(), 1e-9);
        assertEquals(7.75, usage.contextUsagePercentage(), 1e-9);
        assertNull(usage.contextWindowTokens());
        assertEquals("auto", usage.modelId());
    }

    @Test
    void scanIndexesBothStoresAndPrunesADeletedV3Session() throws Exception {
        Path sessionsRoot = tempDir.resolve("sessions");
        Path cliDir = sessionsRoot.resolve("cli");
        Files.createDirectories(cliDir);
        writeV2Session(cliDir, "72897549-046d-4d9f-853c-df9896b9a4df");
        Path sessionJson = V3SessionFixtures.writeV3Session(sessionsRoot, V3_ID, WORKSPACE, "Project overview");

        try (SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"))) {
            service.scanAndIndex(cliDir, sessionsRoot);
            List<SessionManifest> all = service.listAll();
            assertEquals(2, all.size());
            assertTrue(all.stream().anyMatch(m -> m.engine() == SessionEngine.V3 && m.sessionId().equals(V3_ID)));
            assertTrue(all.stream().anyMatch(m -> m.engine() == SessionEngine.V2));
            assertEquals(1, service.search("Swing", 10).size());

            service.scanAndIndex(cliDir, sessionsRoot); // unchanged files: no double-counting
            assertEquals(2, service.listAll().stream().filter(m -> m.engine() == SessionEngine.V3)
                .findFirst().orElseThrow().messageCount());

            Files.walk(sessionJson.getParent()).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
            service.scanAndIndex(cliDir, sessionsRoot);
            assertEquals(1, service.listAll().size());
        }
    }

    @Test
    void aTranscriptThatShrinksIsReindexedFromScratch() throws Exception {
        Path sessionsRoot = tempDir.resolve("sessions");
        Path sessionJson = V3SessionFixtures.writeV3Session(sessionsRoot, V3_ID, WORKSPACE, "Project overview");
        try (SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"))) {
            service.scanAndIndex(null, sessionsRoot);
            assertEquals(2, service.listAll().get(0).messageCount());

            // Simulate /rewind: keep only the first user prompt.
            Path messages = SessionEngine.V3.transcriptFor(sessionJson);
            List<String> lines = Files.readAllLines(messages);
            Files.write(messages, lines.subList(0, 2));
            service.indexOne(sessionJson);

            assertEquals(1, service.listAll().get(0).messageCount());
            assertEquals(0, service.search("Swing", 10).size());
        }
    }

    @Test
    void watcherMapsV3FilesToTheirSessionAndIgnoresBookkeepingFiles() throws Exception {
        Path sessionsRoot = tempDir.resolve("sessions");
        Path sessionJson = V3SessionFixtures.writeV3Session(sessionsRoot, V3_ID, WORKSPACE, "x");
        Path sessionDir = sessionJson.getParent();
        try (SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"))) {
            SessionsWatcher watcher = new SessionsWatcher(sessionsRoot, service);
            Set<Path> out = new LinkedHashSet<>();
            watcher.collectAffectedSidecars(sessionDir.resolve("messages.jsonl"), out);
            watcher.collectAffectedSidecars(sessionDir.resolve("publish.cursor"), out);
            watcher.collectAffectedSidecars(sessionDir.resolve("sub-executions").resolve("abc.jsonl"), out);
            assertEquals(Set.of(sessionJson), out);

            // V2 files count only inside the cli store; stray JSON elsewhere is ignored.
            out.clear();
            Path cliSidecar = sessionsRoot.resolve("cli").resolve("abc.json");
            watcher.collectAffectedSidecars(cliSidecar.resolveSibling("abc.jsonl"), out);
            watcher.collectAffectedSidecars(sessionDir.getParent().resolve("stray.json"), out);
            assertEquals(Set.of(cliSidecar), out);

            // A brand-new workspace folder is scanned for sessions already written inside it.
            out.clear();
            watcher.collectAffectedSidecars(sessionDir.getParent(), out);
            assertEquals(Set.of(sessionJson), out);
        }
    }

    @Test
    @Timeout(30)
    void watcherRootedAtTheSessionsFolderPicksUpANewV3Session() throws Exception {
        Path sessionsRoot = tempDir.resolve("sessions");
        Files.createDirectories(sessionsRoot.resolve("cli"));
        try (SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"))) {
            CountDownLatch latch = new CountDownLatch(1);
            service.setUpdateListener(latch::countDown);
            SessionsWatcher watcher = new SessionsWatcher(sessionsRoot, service);
            watcher.start();
            try {
                V3SessionFixtures.writeV3Session(sessionsRoot, V3_ID, WORKSPACE, "Project overview");
                assertTrue(latch.await(20, TimeUnit.SECONDS), "expected the watcher to notice the new V3 session");
                assertEquals(V3_ID, service.listAll().get(0).sessionId());
            } finally {
                watcher.close();
            }
        }
    }

    @Test
    void continueInV3IsOfferedOnlyForV2Sessions() throws Exception {
        Path sessionsRoot = tempDir.resolve("sessions");
        Path cliDir = sessionsRoot.resolve("cli");
        Files.createDirectories(cliDir);
        writeV2Session(cliDir, "72897549-046d-4d9f-853c-df9896b9a4df");
        V3SessionFixtures.writeV3Session(sessionsRoot, V3_ID, WORKSPACE, "Project overview");

        try (SessionIndexService service = new SessionIndexService(tempDir.resolve("index.db"))) {
            service.scanAndIndex(cliDir, sessionsRoot);
            SessionsPanel panel = new SessionsPanel(new AppPreferences(), service);
            for (int row = 0; row < panel.getTable().getRowCount(); row++) {
                panel.getTable().setRowSelectionInterval(row, row);
                boolean isV3 = "V3".equals(panel.getTable().getValueAt(row, 4));
                assertEquals(!isV3, panel.getContinueInV3Button().isEnabled());
                assertTrue(panel.getResumeInKiroCliButton().isEnabled());
                assertTrue(panel.getSummaryArea().getText().contains("Engine: " + (isV3 ? "V3" : "V2")));
                if (isV3) {
                    assertFalse(panel.getSummaryArea().getText().contains("Started via"));
                    assertTrue(panel.getSummaryArea().getText().contains("Credits used: 1.25"));
                }
            }
        }
    }

    private static void writeV2Session(Path dir, String sessionId) throws Exception {
        Files.writeString(dir.resolve(sessionId + ".json"), """
            {
              "session_id": "%s",
              "cwd": "/tmp",
              "created_at": "2026-08-06T11:26:45.323381992Z",
              "updated_at": "2026-08-06T11:29:38.111897334Z",
              "title": "hello",
              "session_created_reason": "interactive"
            }
            """.formatted(sessionId));
        Files.writeString(dir.resolve(sessionId + ".jsonl"), """
            {"version":"v1","kind":"Prompt","data":{"message_id":"m1","content":[{"kind":"text","data":"hello"}],"meta":{"timestamp":1}}}
            """);
    }
}

package com.ourgiant.kirocontrolpanel.migration;

import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiroCliV3MigrationServiceTest {

    @TempDir
    Path tempDir;

    private static boolean needsUpgrade(String json) throws Exception {
        return KiroCliV3MigrationService.needsV3Upgrade(JsonMapperFactory.createMapper().readTree(json));
    }

    @Test
    void v2AgentWithoutPermissionsNeedsUpgradeButConvertedOrPlainAgentsDoNot() throws Exception {
        assertTrue(needsUpgrade("{\"allowedTools\":[\"fs_read\"]}"));
        assertTrue(needsUpgrade("{\"toolsSettings\":{\"execute_bash\":{}}}"));
        // Converted ("universal") files keep the V2 fields next to the new permissions block.
        assertFalse(needsUpgrade("{\"allowedTools\":[\"fs_read\"],\"permissions\":{\"rules\":[]}}"));
        assertFalse(needsUpgrade("{\"description\":\"plain\",\"tools\":[\"read\"]}"));
        assertFalse(needsUpgrade("[]"));
    }

    @Test
    void parsesVersionAndSavedEngineOutput() {
        assertEquals("2.16.2", KiroCliV3MigrationService.parseVersion("kiro-cli 2.16.2\n"));
        assertEquals("3.0.1", KiroCliV3MigrationService.parseVersion("kiro-cli 3.0.1"));
        assertNull(KiroCliV3MigrationService.parseVersion("garbage"));

        assertEquals("v3", KiroCliV3MigrationService.parseSavedEngine(
            new KiroCliV3MigrationService.CommandResult(List.of(), 0, "\"v3\"\n")));
        assertEquals("v2", KiroCliV3MigrationService.parseSavedEngine(
            new KiroCliV3MigrationService.CommandResult(List.of(), 0, "v2")));
        // Unset: kiro-cli exits 1 with "error: No value associated with chat.agentEngine".
        assertNull(KiroCliV3MigrationService.parseSavedEngine(
            new KiroCliV3MigrationService.CommandResult(List.of(), 1, "error: No value associated")));
    }

    @Test
    void findsLegacyAgentsAcrossFoldersAndCountsBothSessionStores() throws Exception {
        Path global = Files.createDirectories(tempDir.resolve("global-agents"));
        Path workspace = Files.createDirectories(tempDir.resolve("ws-agents"));
        Files.writeString(global.resolve("old.json"), "{\"allowedTools\":[\"fs_read\"]}");
        Files.writeString(global.resolve("new.json"), "{\"permissions\":{\"rules\":[]}}");
        Files.writeString(global.resolve("old.json.bak"), "{\"allowedTools\":[]}");
        Files.writeString(global.resolve("broken.json"), "{not json");
        Files.writeString(workspace.resolve("ws.json"), "{\"toolsSettings\":{}}");

        KiroCliV3MigrationService service = new KiroCliV3MigrationService("kiro-cli-binary-that-does-not-exist");
        List<Path> legacy = service.findLegacyAgents(List.of(global, workspace, tempDir.resolve("missing")));
        assertEquals(List.of(global.resolve("old.json"), workspace.resolve("ws.json")), legacy);

        Path sessions = tempDir.resolve("sessions");
        Path cli = Files.createDirectories(sessions.resolve("cli"));
        Files.writeString(cli.resolve("a.json"), "{}");
        Files.writeString(cli.resolve("a.jsonl"), "");
        Path v3 = Files.createDirectories(sessions.resolve("abc123").resolve("sess_1"));
        Files.writeString(v3.resolve("session.json"), "{}");
        Files.createDirectories(sessions.resolve("abc123").resolve("sess_2")); // no session.json yet
        assertEquals(1, KiroCliV3MigrationService.countV2Sessions(cli));
        assertEquals(1, KiroCliV3MigrationService.countV3Sessions(sessions));
    }

    @Test
    void detectReportsNotInstalledWhenKiroCliCannotRun() {
        KiroCliV3Status status = new KiroCliV3MigrationService("kiro-cli-binary-that-does-not-exist")
            .detect(List.of(), tempDir, tempDir);
        assertFalse(status.cliFound());
        assertFalse(status.shouldSuggestMigration());
    }

    @Test
    void suggestsMigrationOnlyWhenV3IsNotDefaultAndSomethingIsAffected() {
        Path agent = tempDir.resolve("a").resolve("x.json");
        assertTrue(new KiroCliV3Status(true, "2.16.2", null, false, 0, 0, List.of(agent)).shouldSuggestMigration());
        assertTrue(new KiroCliV3Status(true, "3.0.0", "v2", true, 5, 0, List.of()).shouldSuggestMigration());
        // Already using V3 sessions and nothing left to convert: no nagging.
        assertFalse(new KiroCliV3Status(true, "3.0.0", null, true, 5, 2, List.of()).shouldSuggestMigration());
        assertFalse(new KiroCliV3Status(true, "3.0.0", "v3", true, 5, 0, List.of(agent)).shouldSuggestMigration());
        assertFalse(new KiroCliV3Status(true, "3.0.0", null, true, 0, 0, List.of()).shouldSuggestMigration());
        assertEquals(1, new KiroCliV3Status(true, null, null, true, 0, 0,
            List.of(agent, agent.resolveSibling("y.json"))).legacyAgentDirs().size());
    }

    @Test
    void onlyTheTwoKnownEnginesCanBeSaved() {
        KiroCliV3MigrationService service = new KiroCliV3MigrationService("kiro-cli-binary-that-does-not-exist");
        assertThrows(IllegalArgumentException.class, () -> service.setDefaultEngine("--v3; rm -rf /"));
    }

    @Test
    void stripsAnsiColorCodes() {
        assertEquals("✓ upgraded agent", KiroCliV3MigrationService.stripAnsi("\u001B[32m✓\u001B[0m upgraded agent"));
    }
}

package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case here uses a workspace-rooted {@link WorkspaceScope}, never
 * {@link WorkspaceScope#global()} -- that would resolve against the real
 * {@code ~/.kiro}, same reason {@code McpConfigServiceTest} et al. avoid it.
 */
class KiroSetupScannerTest {

    @TempDir
    Path workspaceRoot;

    private final KiroSetupScanner scanner = new KiroSetupScanner();
    private WorkspaceScope scope;
    private Path kiroDir;

    @BeforeEach
    void setUp() {
        scope = new WorkspaceScope("test", workspaceRoot);
        kiroDir = workspaceRoot.resolve(".kiro");
    }

    // --- MCP ---

    @Test
    void mcpMissingFileHasNoFindings() {
        assertTrue(scanner.scanMcp(scope).isEmpty());
    }

    @Test
    void mcpWellFormedServersHaveNoFindings() throws IOException {
        Path settingsDir = kiroDir.resolve("settings");
        Files.createDirectories(settingsDir);
        Files.writeString(settingsDir.resolve("mcp.json"), """
            {
              "mcpServers": {
                "fetch": { "command": "uvx", "args": ["mcp-server-fetch"] },
                "remote-one": { "url": "https://example.com/mcp" }
              }
            }
            """);

        assertTrue(scanner.scanMcp(scope).isEmpty());
    }

    @Test
    void mcpInvalidJsonIsFlaggedInvalid() throws IOException {
        Path settingsDir = kiroDir.resolve("settings");
        Files.createDirectories(settingsDir);
        Files.writeString(settingsDir.resolve("mcp.json"), "{ not valid json");

        List<Finding> findings = scanner.scanMcp(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.INVALID, findings.get(0).severity());
        assertFalse(findings.get(0).isFixable());
    }

    @Test
    void mcpServerMissingCommandAndUrlIsFlaggedStructural() throws IOException {
        Path settingsDir = kiroDir.resolve("settings");
        Files.createDirectories(settingsDir);
        Files.writeString(settingsDir.resolve("mcp.json"), """
            { "mcpServers": { "broken": { "disabled": false } } }
            """);

        List<Finding> findings = scanner.scanMcp(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
        assertTrue(findings.get(0).message().contains("broken"));
    }

    // --- Steering ---

    @Test
    void steeringPlainDocHasNoFindings() throws IOException {
        Path steeringDir = kiroDir.resolve("steering");
        Files.createDirectories(steeringDir);
        Files.writeString(steeringDir.resolve("tech.md"), "# Tech Stack\n");

        assertTrue(scanner.scanSteering(scope).isEmpty());
    }

    @Test
    void steeringMalformedFrontMatterIsFlaggedInvalid() throws IOException {
        Path steeringDir = kiroDir.resolve("steering");
        Files.createDirectories(steeringDir);
        Files.writeString(steeringDir.resolve("broken.md"), """
            ---
            inclusion: "unterminated
            ---
            Body
            """);

        List<Finding> findings = scanner.scanSteering(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.INVALID, findings.get(0).severity());
    }

    @Test
    void steeringFileMatchWithoutPatternIsFlaggedStructural() throws IOException {
        Path steeringDir = kiroDir.resolve("steering");
        Files.createDirectories(steeringDir);
        Files.writeString(steeringDir.resolve("react.md"), """
            ---
            inclusion: fileMatch
            ---
            # React
            """);

        List<Finding> findings = scanner.scanSteering(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
        assertTrue(findings.get(0).message().contains("fileMatchPattern"));
    }

    @Test
    void steeringAutoMissingDescriptionIsFlaggedStructural() throws IOException {
        Path steeringDir = kiroDir.resolve("steering");
        Files.createDirectories(steeringDir);
        Files.writeString(steeringDir.resolve("api.md"), """
            ---
            inclusion: auto
            name: api-conventions
            ---
            # API
            """);

        List<Finding> findings = scanner.scanSteering(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
    }

    // --- Skills ---

    @Test
    void skillsLooseMarkdownFileIsFlaggedWithRelocationFix() throws IOException {
        Path skillsDir = kiroDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Files.writeString(skillsDir.resolve("my-skill.md"), """
            ---
            name: my-skill
            description: does things
            ---
            Body
            """);

        List<Finding> findings = scanner.scanSkills(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
        assertInstanceOf(SkillFileRelocationFix.class, findings.get(0).fix());
    }

    @Test
    void skillsWellFormedSkillHasNoFindings() throws IOException {
        Path skillDir = kiroDir.resolve("skills").resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: my-skill
            description: does things
            ---
            Body
            """);

        assertTrue(scanner.scanSkills(scope).isEmpty());
    }

    @Test
    void skillsDirectoryWithoutSkillMdIsSilentlySkipped() throws IOException {
        Path skillDir = kiroDir.resolve("skills").resolve("in-progress");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("notes.txt"), "not a skill yet");

        assertTrue(scanner.scanSkills(scope).isEmpty());
    }

    @Test
    void skillsMalformedFrontMatterIsFlaggedInvalid() throws IOException {
        Path skillDir = kiroDir.resolve("skills").resolve("broken-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: "unterminated
            ---
            Body
            """);

        List<Finding> findings = scanner.scanSkills(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.INVALID, findings.get(0).severity());
    }

    // --- Hooks ---

    @Test
    void hooksWellFormedFileHasNoFindings() throws IOException {
        Path hooksDir = kiroDir.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("lint.json"), """
            {
              "version": "v1",
              "hooks": [
                { "name": "lint", "trigger": "file_save", "action": { "type": "command", "command": "echo hi" } }
              ]
            }
            """);

        assertTrue(scanner.scanHooks(scope).isEmpty());
    }

    @Test
    void hooksInvalidJsonIsFlaggedInvalid() throws IOException {
        Path hooksDir = kiroDir.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("broken.json"), "{ not valid json");

        List<Finding> findings = scanner.scanHooks(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.INVALID, findings.get(0).severity());
    }

    @Test
    void hooksBareArrayIsFlaggedWithWrapFix() throws IOException {
        Path hooksDir = kiroDir.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("bare.json"), """
            [
              { "name": "lint", "trigger": "file_save", "action": { "type": "command", "command": "echo hi" } }
            ]
            """);

        List<Finding> findings = scanner.scanHooks(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
        assertInstanceOf(HookArrayWrapFix.class, findings.get(0).fix());
    }

    @Test
    void hooksEntryMissingTriggerIsFlaggedStructural() throws IOException {
        Path hooksDir = kiroDir.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("incomplete.json"), """
            {
              "version": "v1",
              "hooks": [
                { "name": "no-trigger", "action": { "type": "command", "command": "echo hi" } }
              ]
            }
            """);

        List<Finding> findings = scanner.scanHooks(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.STRUCTURAL, findings.get(0).severity());
    }

    @Test
    void hooksSkippedEntirelyForGlobalScope() throws IOException {
        Path hooksDir = kiroDir.resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("broken.json"), "{ not valid json");

        assertTrue(scanner.scanHooks(WorkspaceScope.global()).isEmpty());
    }

    // --- Agents ---

    @Test
    void agentsWellFormedFileHasNoFindings() throws IOException {
        Path agentsDir = kiroDir.resolve("agents");
        Files.createDirectories(agentsDir);
        Files.writeString(agentsDir.resolve("reviewer.json"), """
            { "description": "Reviews code", "prompt": "Review this." }
            """);

        assertTrue(scanner.scanAgents(scope).isEmpty());
    }

    @Test
    void agentsInvalidJsonIsFlaggedInvalid() throws IOException {
        Path agentsDir = kiroDir.resolve("agents");
        Files.createDirectories(agentsDir);
        Files.writeString(agentsDir.resolve("broken.json"), "{ not valid json");

        List<Finding> findings = scanner.scanAgents(scope);

        assertEquals(1, findings.size());
        assertEquals(FindingSeverity.INVALID, findings.get(0).severity());
    }

    // --- top-level scan() ---

    @Test
    void scanAggregatesAcrossAllSurfacesAndScopes() throws IOException {
        Path settingsDir = kiroDir.resolve("settings");
        Files.createDirectories(settingsDir);
        Files.writeString(settingsDir.resolve("mcp.json"), """
            { "mcpServers": { "broken": {} } }
            """);

        // A second, distinct workspace root (not WorkspaceScope.global(), which would resolve
        // against this machine's real ~/.kiro) -- proves scan() aggregates across scopes, not
        // just surfaces.
        Path secondWorkspaceRoot = workspaceRoot.resolve("second-workspace");
        Path secondHooksDir = secondWorkspaceRoot.resolve(".kiro").resolve("hooks");
        Files.createDirectories(secondHooksDir);
        Files.writeString(secondHooksDir.resolve("broken.json"), "{ not valid json");
        WorkspaceScope secondScope = new WorkspaceScope("second", secondWorkspaceRoot);

        List<Finding> findings = scanner.scan(List.of(scope, secondScope));

        assertEquals(2, findings.size());
    }
}

package com.ourgiant.kirocontrolpanel.hooks;

import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookServiceTest {

    @TempDir
    Path workspaceRoot;

    private final HookService service = new HookService();
    private Path hooksDir;

    @BeforeEach
    void setUp() throws IOException {
        hooksDir = workspaceRoot.resolve(".kiro").resolve("hooks");
        Files.createDirectories(hooksDir);
    }

    @Test
    void createHookFileThenListRoundTrips() throws IOException {
        Hook hook = new Hook();
        hook.setName("lint-on-save");
        hook.setTrigger("file_save");
        hook.setMatcher("**/*.java");
        hook.setEnabled(true);
        hook.setTimeout(30);
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_COMMAND);
        action.setCommand("mvn -q checkstyle:check");
        hook.setAction(action);

        service.createHookFile(workspaceRoot, "lint-on-save", hook);
        List<HookEntry> entries = service.listWorkspace(workspaceRoot);

        assertEquals(1, entries.size());
        Hook reloaded = entries.get(0).getHook();
        assertEquals("lint-on-save", reloaded.getName());
        assertEquals("file_save", reloaded.getTrigger());
        assertEquals("**/*.java", reloaded.getMatcher());
        assertEquals(30, reloaded.getTimeout());
        assertEquals(HookAction.TYPE_COMMAND, reloaded.getAction().getType());
        assertEquals("mvn -q checkstyle:check", reloaded.getAction().getCommand());
    }

    @Test
    void agentPromptActionRoundTrips() throws IOException {
        Hook hook = new Hook();
        hook.setName("summarize");
        hook.setTrigger("agent_stop");
        hook.setEnabled(true);
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_AGENT);
        action.setPrompt("Summarize what changed this session.");
        hook.setAction(action);

        service.createHookFile(workspaceRoot, "summarize", hook);
        List<HookEntry> entries = service.listWorkspace(workspaceRoot);

        HookAction reloadedAction = entries.get(0).getHook().getAction();
        assertEquals(HookAction.TYPE_AGENT, reloadedAction.getType());
        assertEquals("Summarize what changed this session.", reloadedAction.getPrompt());
    }

    @Test
    void listFlattensHooksAcrossMultipleFiles() throws IOException {
        Hook a = new Hook();
        a.setName("hook-a");
        a.setTrigger("file_save");
        Hook b = new Hook();
        b.setName("hook-b");
        b.setTrigger("prompt_submit");

        service.createHookFile(workspaceRoot, "file-a", a);
        service.createHookFile(workspaceRoot, "file-b", b);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);

        assertEquals(2, entries.size());
        assertEquals(List.of("hook-a", "hook-b"),
            entries.stream().map(e -> e.getHook().getName()).sorted().toList());
    }

    @Test
    void deleteRemovesFileWhenLastHookInIt() throws IOException {
        Hook hook = new Hook();
        hook.setName("only-hook");
        hook.setTrigger("file_save");
        Path filePath = service.createHookFile(workspaceRoot, "only-hook", hook);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);
        service.delete(entries.get(0));

        assertFalse(Files.exists(filePath));
    }

    @Test
    void deleteKeepsFileWhenOtherHooksRemain() throws IOException {
        Path filePath = hooksDir.resolve("shared.json");
        Files.writeString(filePath, """
            {
              "version": "v1",
              "hooks": [
                { "name": "keep-me", "trigger": "file_save", "enabled": true },
                { "name": "remove-me", "trigger": "file_save", "enabled": true }
              ]
            }
            """);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);
        HookEntry toRemove = entries.stream()
            .filter(e -> "remove-me".equals(e.getHook().getName()))
            .findFirst().orElseThrow();

        service.delete(toRemove);

        assertTrue(Files.exists(filePath));
        List<HookEntry> remaining = service.listWorkspace(workspaceRoot);
        assertEquals(1, remaining.size());
        assertEquals("keep-me", remaining.get(0).getHook().getName());
    }

    @Test
    void preservesUnknownFieldsOnHookAndAction() throws IOException {
        Path filePath = hooksDir.resolve("custom.json");
        Files.writeString(filePath, """
            {
              "version": "v1",
              "hooks": [
                {
                  "name": "custom-hook",
                  "trigger": "file_save",
                  "enabled": true,
                  "futureHookField": "keep-me",
                  "action": { "type": "command", "command": "echo hi", "futureActionField": "keep-me-too" }
                }
              ]
            }
            """);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);
        service.save(filePath, entries.get(0).getFile());
        String rewritten = Files.readString(filePath);

        assertTrue(rewritten.contains("keep-me"));
        assertTrue(rewritten.contains("keep-me-too"));
    }

    @Test
    void creatingModifyingAndDeletingRecordChangeLogEntries() throws IOException {
        Hook hook = new Hook();
        hook.setName("logged-hook");
        hook.setTrigger("file_save");
        Path filePath = service.createHookFile(workspaceRoot, "logged-hook", hook);
        assertEquals(List.of(ChangeKind.CREATED), kindsFor(filePath));

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);
        HookEntry entry = entries.get(0);
        service.save(entry.getFilePath(), entry.getFile());
        assertEquals(List.of(ChangeKind.CREATED, ChangeKind.MODIFIED), kindsFor(filePath));

        service.delete(entry);
        assertEquals(List.of(ChangeKind.CREATED, ChangeKind.MODIFIED, ChangeKind.DELETED), kindsFor(filePath));
    }

    /** Filters the (shared, whole-test-suite) change log down to entries for one specific path, in recorded order. */
    private static List<ChangeKind> kindsFor(Path path) {
        return ChangeLogService.loadSince(Instant.EPOCH).reversed().stream()
            .filter(e -> e.path().equals(path.toAbsolutePath().normalize()))
            .map(e -> e.kindEnum())
            .toList();
    }

    @Test
    void copyPreservesUnknownFieldsAndIsIndependentOfTheOriginal() {
        Hook original = new Hook();
        original.setName("original-hook");
        original.setTrigger("file_save");
        original.setMatcher("**/*.java");
        original.setEnabled(true);
        original.setTimeout(30);
        original.putExtra("futureHookField", "keep-me");
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_COMMAND);
        action.setCommand("echo hi");
        action.putExtra("futureActionField", "keep-me-too");
        original.setAction(action);

        Hook copy = service.copy(original);

        assertEquals("original-hook", copy.getName());
        assertEquals("file_save", copy.getTrigger());
        assertEquals("**/*.java", copy.getMatcher());
        assertEquals(30, copy.getTimeout());
        assertEquals("keep-me", copy.getExtra().get("futureHookField"));
        assertEquals(HookAction.TYPE_COMMAND, copy.getAction().getType());
        assertEquals("echo hi", copy.getAction().getCommand());
        assertEquals("keep-me-too", copy.getAction().getExtra().get("futureActionField"));

        copy.setName("renamed-copy");
        assertEquals("original-hook", original.getName(), "mutating the copy must not affect the original");
    }

    @Test
    void listWorkspaceReturnsEmptyWhenHooksDirMissing() throws IOException {
        Files.delete(hooksDir);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);

        assertTrue(entries.isEmpty());
    }
}

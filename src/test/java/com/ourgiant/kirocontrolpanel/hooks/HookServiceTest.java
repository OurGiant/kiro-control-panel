package com.ourgiant.kirocontrolpanel.hooks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void listWorkspaceReturnsEmptyWhenHooksDirMissing() throws IOException {
        Files.delete(hooksDir);

        List<HookEntry> entries = service.listWorkspace(workspaceRoot);

        assertTrue(entries.isEmpty());
    }
}

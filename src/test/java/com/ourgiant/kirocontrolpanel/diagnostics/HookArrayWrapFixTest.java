package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.hooks.Hook;
import com.ourgiant.kirocontrolpanel.hooks.HookAction;
import com.ourgiant.kirocontrolpanel.hooks.HookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookArrayWrapFixTest {

    @TempDir
    Path workspaceRoot;

    private final KiroSetupScanner scanner = new KiroSetupScanner();

    @Test
    void applyWrapsBareArrayInVersionedEnvelope() throws IOException {
        Path hooksDir = workspaceRoot.resolve(".kiro").resolve("hooks");
        Files.createDirectories(hooksDir);
        Path path = hooksDir.resolve("bare.json");
        Files.writeString(path, """
            [
              { "name": "lint", "trigger": "file_save", "action": { "type": "command", "command": "echo hi" } }
            ]
            """);

        Hook hook = new Hook();
        hook.setName("lint");
        hook.setTrigger("file_save");
        HookAction action = new HookAction();
        action.setType(HookAction.TYPE_COMMAND);
        action.setCommand("echo hi");
        hook.setAction(action);

        new HookArrayWrapFix(path, List.of(hook), new HookService()).apply();

        String rewritten = Files.readString(path);
        assertTrue(rewritten.contains("\"version\""));
        assertTrue(rewritten.contains("\"hooks\""));
        assertTrue(rewritten.contains("lint"));
    }

    @Test
    void appliedFixMakesSubsequentScanClean() throws IOException {
        Path hooksDir = workspaceRoot.resolve(".kiro").resolve("hooks");
        Files.createDirectories(hooksDir);
        Files.writeString(hooksDir.resolve("bare.json"), """
            [
              { "name": "lint", "trigger": "file_save", "action": { "type": "command", "command": "echo hi" } }
            ]
            """);
        WorkspaceScope scope = new WorkspaceScope("test", workspaceRoot);

        List<Finding> before = scanner.scanHooks(scope);
        assertEquals(1, before.size());
        before.get(0).fix().apply();

        assertTrue(scanner.scanHooks(scope).isEmpty());
    }
}

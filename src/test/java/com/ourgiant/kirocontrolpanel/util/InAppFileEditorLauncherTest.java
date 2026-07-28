package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.hooks.Hook;
import com.ourgiant.kirocontrolpanel.hooks.HookEntry;
import com.ourgiant.kirocontrolpanel.hooks.HookFile;
import com.ourgiant.kirocontrolpanel.hooks.HookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InAppFileEditorLauncherTest {

    @TempDir
    Path workspaceRoot;

    private final HookService service = new HookService();

    @Test
    void resolvesTheSingleHookInAFile() throws IOException {
        Hook hook = new Hook();
        hook.setName("lint");
        hook.setTrigger("file_save");
        Path filePath = service.createHookFile(workspaceRoot, "lint", hook);

        Optional<HookEntry> resolved = InAppFileEditorLauncher.resolveHookEntry(service, workspaceRoot, filePath);

        assertTrue(resolved.isPresent());
        assertEquals("lint", resolved.get().getHook().getName());
    }

    @Test
    void ambiguousWhenMultipleHooksShareOneFile() throws IOException {
        Hook a = new Hook();
        a.setName("hook-a");
        a.setTrigger("file_save");
        Hook b = new Hook();
        b.setName("hook-b");
        b.setTrigger("file_save");
        Path filePath = workspaceRoot.resolve(".kiro").resolve("hooks").resolve("shared.json");
        java.nio.file.Files.createDirectories(filePath.getParent());
        HookFile hookFile = new HookFile();
        hookFile.getHooks().add(a);
        hookFile.getHooks().add(b);
        service.save(filePath, hookFile);

        Optional<HookEntry> resolved = InAppFileEditorLauncher.resolveHookEntry(service, workspaceRoot, filePath);

        assertTrue(resolved.isEmpty(), "expected ambiguity (2 hooks in one file) to resolve to empty");
    }

    @Test
    void emptyWhenNoHookMatchesThePath() throws IOException {
        Hook hook = new Hook();
        hook.setName("lint");
        hook.setTrigger("file_save");
        service.createHookFile(workspaceRoot, "lint", hook);
        Path unrelated = workspaceRoot.resolve(".kiro").resolve("hooks").resolve("does-not-exist.json");

        Optional<HookEntry> resolved = InAppFileEditorLauncher.resolveHookEntry(service, workspaceRoot, unrelated);

        assertTrue(resolved.isEmpty());
    }
}

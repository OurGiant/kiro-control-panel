package com.ourgiant.kirocontrolpanel.hooks;

import java.nio.file.Path;

/**
 * A hook plus a pointer back to the file (and in-memory {@link HookFile}) it
 * came from, so edits can be saved back to the right place — a workspace's
 * hooks can be spread across several files.
 */
public class HookEntry {
    private final Path filePath;
    private final HookFile file;
    private final Hook hook;

    public HookEntry(Path filePath, HookFile file, Hook hook) {
        this.filePath = filePath;
        this.file = file;
        this.hook = hook;
    }

    public Path getFilePath() {
        return filePath;
    }

    public HookFile getFile() {
        return file;
    }

    public Hook getHook() {
        return hook;
    }
}

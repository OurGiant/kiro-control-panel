package com.ourgiant.kirocontrolpanel.hooks;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import com.ourgiant.kirocontrolpanel.changelog.ChangeSource;
import com.ourgiant.kirocontrolpanel.util.JsonMapperFactory;
import com.ourgiant.kirocontrolpanel.util.SelfWriteTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads and writes .kiro/hooks/*.json — workspace-only, per Kiro's schema
 * (there's no global hooks directory). Each file holds
 * {@code {"version":"v1","hooks":[...]}}; a workspace can have multiple hook
 * files, so hooks are flattened across all of them for display but saved
 * back to their own originating file.
 */
public class HookService {
    private static final Logger logger = LoggerFactory.getLogger(HookService.class);

    private final ObjectMapper mapper = JsonMapperFactory.createMapper();
    private final PrettyPrinter prettyPrinter = JsonMapperFactory.createPrettyPrinter();

    public List<HookEntry> listWorkspace(Path workspaceRoot) {
        List<HookEntry> entries = new ArrayList<>();
        Path hooksDir = KiroPaths.workspaceHooksDir(workspaceRoot);
        if (!Files.isDirectory(hooksDir)) {
            return entries;
        }
        try (Stream<Path> files = Files.list(hooksDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .sorted()
                .forEach(path -> {
                    try {
                        HookFile hookFile = mapper.readValue(path.toFile(), HookFile.class);
                        for (Hook hook : hookFile.getHooks()) {
                            entries.add(new HookEntry(path, hookFile, hook));
                        }
                    } catch (IOException e) {
                        logger.warn("Failed to read hook file {}", path, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list hooks directory {}", hooksDir, e);
        }
        return entries;
    }

    public void save(Path filePath, HookFile file) throws IOException {
        boolean existedBefore = Files.exists(filePath);
        SelfWriteTracker.markAboutToWrite(filePath.getParent());
        Files.createDirectories(filePath.getParent());
        SelfWriteTracker.markAboutToWrite(filePath);
        mapper.writer(prettyPrinter).writeValue(filePath.toFile(), file);
        logger.info("Saved hooks file {}", filePath);
        ChangeLogService.record(filePath, existedBefore ? ChangeKind.MODIFIED : ChangeKind.CREATED, ChangeSource.SELF);
    }

    /** Creates a new single-hook file, e.g. for the Hooks panel's "Add..." action. */
    public Path createHookFile(Path workspaceRoot, String fileNameSlug, Hook hook) throws IOException {
        Path filePath = KiroPaths.workspaceHooksDir(workspaceRoot).resolve(fileNameSlug + ".json");
        HookFile hookFile = new HookFile();
        hookFile.getHooks().add(hook);
        save(filePath, hookFile);
        return filePath;
    }

    /**
     * Deep-copies a {@link Hook} (including its {@link HookAction} and both objects' unmodeled
     * {@code extra} fields) via a Jackson round-trip, for the Hooks panel's "Duplicate" action --
     * a manual field-by-field copy would silently drop anything only carried in {@code extra}.
     */
    public Hook copy(Hook original) {
        return mapper.convertValue(original, Hook.class);
    }

    /** Removes a hook from its file; deletes the file entirely if it was the last hook in it. */
    public void delete(HookEntry entry) throws IOException {
        entry.getFile().getHooks().remove(entry.getHook());
        if (entry.getFile().getHooks().isEmpty()) {
            boolean existed = Files.deleteIfExists(entry.getFilePath());
            if (existed) {
                logger.info("Deleted empty hooks file {}", entry.getFilePath());
                ChangeLogService.record(entry.getFilePath(), ChangeKind.DELETED, ChangeSource.SELF);
            }
        } else {
            save(entry.getFilePath(), entry.getFile());
        }
    }
}

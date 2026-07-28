package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogEntry;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillFileRelocationFixTest {

    @TempDir
    Path workspaceRoot;

    private final KiroSetupScanner scanner = new KiroSetupScanner();

    @Test
    void applyMovesLooseFileIntoOwnSubfolderAsSkillMd() throws IOException {
        Path skillsDir = workspaceRoot.resolve(".kiro").resolve("skills");
        Files.createDirectories(skillsDir);
        Path looseFile = skillsDir.resolve("my-skill.md");
        Files.writeString(looseFile, """
            ---
            name: my-skill
            description: does things
            ---
            Body
            """);

        new SkillFileRelocationFix(looseFile).apply();

        Path relocated = skillsDir.resolve("my-skill").resolve("SKILL.md");
        assertTrue(Files.isRegularFile(relocated));
        assertFalse(Files.exists(looseFile));
        assertTrue(Files.readString(relocated).contains("name: my-skill"));
    }

    @Test
    void appliedFixMakesSubsequentScanClean() throws IOException {
        Path skillsDir = workspaceRoot.resolve(".kiro").resolve("skills");
        Files.createDirectories(skillsDir);
        Path looseFile = skillsDir.resolve("my-skill.md");
        Files.writeString(looseFile, """
            ---
            name: my-skill
            description: does things
            ---
            Body
            """);
        WorkspaceScope scope = new WorkspaceScope("test", workspaceRoot);

        List<Finding> before = scanner.scanSkills(scope);
        assertEquals(1, before.size());
        before.get(0).fix().apply();

        assertTrue(scanner.scanSkills(scope).isEmpty());
    }

    @Test
    void applyDoesNotOverwriteAnExistingDestination() throws IOException {
        Path skillsDir = workspaceRoot.resolve(".kiro").resolve("skills");
        Path existingSkillDir = skillsDir.resolve("my-skill");
        Files.createDirectories(existingSkillDir);
        Files.writeString(existingSkillDir.resolve("SKILL.md"), "existing content");
        Path looseFile = skillsDir.resolve("my-skill.md");
        Files.writeString(looseFile, "loose content");

        assertThrows(IOException.class, () -> new SkillFileRelocationFix(looseFile).apply());
        assertEquals("existing content", Files.readString(existingSkillDir.resolve("SKILL.md")));
    }

    @Test
    void applyRecordsDeletedAndCreatedChangeLogEntries() throws IOException {
        Path skillsDir = workspaceRoot.resolve(".kiro").resolve("skills");
        Files.createDirectories(skillsDir);
        Path looseFile = skillsDir.resolve("my-skill.md");
        Files.writeString(looseFile, "---\nname: my-skill\ndescription: d\n---\nBody\n");
        Path relocated = skillsDir.resolve("my-skill").resolve("SKILL.md");

        new SkillFileRelocationFix(looseFile).apply();

        List<ChangeLogEntry> entries = ChangeLogService.loadSince(Instant.EPOCH);
        assertTrue(entries.stream().anyMatch(
            e -> e.path().equals(looseFile.toAbsolutePath().normalize()) && e.kindEnum() == ChangeKind.DELETED));
        assertTrue(entries.stream().anyMatch(
            e -> e.path().equals(relocated.toAbsolutePath().normalize()) && e.kindEnum() == ChangeKind.CREATED));
    }
}

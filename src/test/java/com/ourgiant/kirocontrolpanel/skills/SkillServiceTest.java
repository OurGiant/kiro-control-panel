package com.ourgiant.kirocontrolpanel.skills;

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

class SkillServiceTest {

    @TempDir
    Path workspaceRoot;

    private final SkillService service = new SkillService();
    private Path skillsDir;

    @BeforeEach
    void setUp() throws IOException {
        skillsDir = workspaceRoot.resolve(".kiro").resolve("skills");
        Files.createDirectories(skillsDir);
    }

    @Test
    void savedSkillRoundTripsThroughLoad() throws IOException {
        Skill skill = new Skill(skillsDir.resolve("pdf-processing"), workspaceRoot);
        skill.setName("pdf-processing");
        skill.setDescription("Extract text and tables from PDF files");
        skill.setBody("## Instructions\n\nUse pdftotext for extraction.\n");

        service.save(skill);
        Skill reloaded = service.load(skillsDir.resolve("pdf-processing"), workspaceRoot);

        assertEquals("pdf-processing", reloaded.getName());
        assertEquals("Extract text and tables from PDF files", reloaded.getDescription());
        assertEquals("## Instructions\n\nUse pdftotext for extraction.\n", reloaded.getBody());
    }

    @Test
    void preservesUnknownFrontMatterFields() throws IOException {
        Path skillDir = skillsDir.resolve("custom-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: custom-skill
            description: A skill with extra fields
            license: MIT
            version: "1.0"
            ---
            Body text.
            """);

        Skill skill = service.load(skillDir, workspaceRoot);
        service.save(skill);
        String rewritten = Files.readString(skillDir.resolve("SKILL.md"));

        assertTrue(rewritten.contains("license"));
        assertTrue(rewritten.contains("MIT"));
        assertTrue(rewritten.contains("version"));
    }

    @Test
    void listWorkspaceOnlyIncludesFoldersWithSkillMd() throws IOException {
        Path withSkillMd = skillsDir.resolve("real-skill");
        Files.createDirectories(withSkillMd);
        Files.writeString(withSkillMd.resolve("SKILL.md"), "---\nname: real-skill\ndescription: d\n---\nBody\n");

        Path withoutSkillMd = skillsDir.resolve("not-a-skill");
        Files.createDirectories(withoutSkillMd);
        Files.writeString(withoutSkillMd.resolve("notes.txt"), "just a folder, no SKILL.md");

        List<Skill> skills = service.listWorkspace(workspaceRoot);

        assertEquals(1, skills.size());
        assertEquals("real-skill", skills.get(0).getSkillFolderName());
    }

    @Test
    void deleteRemovesEntireSkillFolderIncludingAssets() throws IOException {
        Skill skill = new Skill(skillsDir.resolve("with-assets"), workspaceRoot);
        skill.setName("with-assets");
        skill.setDescription("Has bundled scripts");
        skill.setBody("Body\n");
        service.save(skill);

        Path scriptsDir = skill.getSkillDir().resolve("scripts");
        Files.createDirectories(scriptsDir);
        Files.writeString(scriptsDir.resolve("run.sh"), "#!/bin/sh\necho hi\n");

        assertTrue(Files.exists(skill.getSkillDir()));

        service.delete(skill);

        assertFalse(Files.exists(skill.getSkillDir()));
    }

    @Test
    void listWorkspaceReturnsEmptyWhenSkillsDirMissing() throws IOException {
        Files.delete(skillsDir);

        List<Skill> skills = service.listWorkspace(workspaceRoot);

        assertTrue(skills.isEmpty());
    }
}

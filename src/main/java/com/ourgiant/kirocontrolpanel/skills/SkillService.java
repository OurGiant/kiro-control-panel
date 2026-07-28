package com.ourgiant.kirocontrolpanel.skills;

import com.ourgiant.kirocontrolpanel.KiroPaths;
import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import com.ourgiant.kirocontrolpanel.changelog.ChangeSource;
import com.ourgiant.kirocontrolpanel.util.FrontMatterParser;
import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.SelfWriteTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads and writes .kiro/skills/&lt;name&gt;/SKILL.md — the exact files Kiro
 * itself reads. Kiro requires SKILL.md to live in a subfolder of skills/, not
 * loose at the top level, so listing only considers directories.
 */
public class SkillService {
    private static final Logger logger = LoggerFactory.getLogger(SkillService.class);
    private static final String SKILL_MD = "SKILL.md";

    public List<Skill> listGlobal() {
        return list(KiroPaths.globalSkillsDir(), null);
    }

    public List<Skill> listWorkspace(Path workspaceRoot) {
        return list(KiroPaths.workspaceSkillsDir(workspaceRoot), workspaceRoot);
    }

    private List<Skill> list(Path skillsDir, Path workspaceRoot) {
        List<Skill> skills = new ArrayList<>();
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }
        try (Stream<Path> entries = Files.list(skillsDir)) {
            entries.filter(Files::isDirectory)
                .sorted()
                .forEach(dir -> {
                    if (Files.isRegularFile(dir.resolve(SKILL_MD))) {
                        try {
                            skills.add(load(dir, workspaceRoot));
                        } catch (IOException e) {
                            logger.warn("Failed to read skill {}", dir, e);
                        }
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list skills directory {}", skillsDir, e);
        }
        return skills;
    }

    public Skill load(Path skillDir, Path workspaceRoot) throws IOException {
        String content = Files.readString(skillDir.resolve(SKILL_MD), StandardCharsets.UTF_8);
        FrontMatterParser.Document parsed = FrontMatterParser.parse(content);

        Skill skill = new Skill(skillDir, workspaceRoot);
        Map<String, Object> fm = new LinkedHashMap<>(parsed.frontMatter());
        Object name = fm.remove("name");
        Object description = fm.remove("description");
        skill.setName(name == null ? skillDir.getFileName().toString() : String.valueOf(name));
        skill.setDescription(description == null ? "" : String.valueOf(description));
        skill.getExtraFrontMatter().putAll(fm);
        skill.setBody(parsed.body());
        return skill;
    }

    public void save(Skill skill) throws IOException {
        boolean existedBefore = Files.exists(skill.getSkillMdPath());
        // Mark the skill's own directory before creating it: every skill lives in its own
        // directory, so this is a real ENTRY_CREATE event on every new skill, not an edge case.
        SelfWriteTracker.markAboutToWrite(skill.getSkillDir());
        Files.createDirectories(skill.getSkillDir());

        Map<String, Object> frontMatter = new LinkedHashMap<>();
        frontMatter.put("name", skill.getName());
        frontMatter.put("description", skill.getDescription());
        frontMatter.putAll(skill.getExtraFrontMatter());

        String content = FrontMatterParser.serialize(frontMatter, skill.getBody());
        SelfWriteTracker.markAboutToWrite(skill.getSkillMdPath());
        Files.writeString(skill.getSkillMdPath(), content, StandardCharsets.UTF_8);
        logger.info("Saved skill {}", skill.getSkillMdPath());
        GitAutoCommitter.commitIfEnabled(skill.getSkillMdPath());
        ChangeLogService.record(skill.getSkillMdPath(),
            existedBefore ? ChangeKind.MODIFIED : ChangeKind.CREATED, ChangeSource.SELF);
    }

    public void delete(Skill skill) throws IOException {
        boolean existed = Files.exists(skill.getSkillDir());
        deleteRecursively(skill.getSkillDir());
        if (existed) {
            logger.info("Deleted skill {}", skill.getSkillDir());
            ChangeLogService.record(skill.getSkillMdPath(), ChangeKind.DELETED, ChangeSource.SELF);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}

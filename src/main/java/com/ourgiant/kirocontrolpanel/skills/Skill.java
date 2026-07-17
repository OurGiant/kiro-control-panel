package com.ourgiant.kirocontrolpanel.skills;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A skill folder: SKILL.md front matter (name/description, required — Kiro
 * matches requests against them) plus any other front-matter keys we don't
 * model explicitly, preserved in {@code extraFrontMatter} so editing a skill
 * here never drops fields this tool doesn't understand yet. {@code
 * workspaceRoot} is null for a global (~/.kiro/skills) skill.
 */
public class Skill {
    private final Path skillDir;
    private final Path workspaceRoot;

    private String name = "";
    private String description = "";
    private final Map<String, Object> extraFrontMatter = new LinkedHashMap<>();
    private String body = "";

    public Skill(Path skillDir, Path workspaceRoot) {
        this.skillDir = skillDir;
        this.workspaceRoot = workspaceRoot;
    }

    public Path getSkillDir() {
        return skillDir;
    }

    public Path getSkillMdPath() {
        return skillDir.resolve("SKILL.md");
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public boolean isGlobal() {
        return workspaceRoot == null;
    }

    public String getSkillFolderName() {
        return skillDir.getFileName().toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getExtraFrontMatter() {
        return extraFrontMatter;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}

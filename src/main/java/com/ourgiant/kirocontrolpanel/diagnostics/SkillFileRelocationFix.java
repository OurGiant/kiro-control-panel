package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.changelog.ChangeKind;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogService;
import com.ourgiant.kirocontrolpanel.changelog.ChangeSource;
import com.ourgiant.kirocontrolpanel.util.GitAutoCommitter;
import com.ourgiant.kirocontrolpanel.util.SelfWriteTracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Repairs a {@code .md} file sitting loose directly in {@code skills/}
 * (invisible to Kiro/{@code SkillService} today, since only subfolders
 * containing a {@code SKILL.md} are recognized) by moving it into its own
 * subfolder as {@code SKILL.md}, matching Kiro's required layout.
 */
public final class SkillFileRelocationFix implements Fix {
    private final Path looseFile;

    public SkillFileRelocationFix(Path looseFile) {
        this.looseFile = looseFile;
    }

    private String skillName() {
        String fileName = looseFile.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".md".length());
    }

    private Path destination() {
        return looseFile.resolveSibling(skillName()).resolve("SKILL.md");
    }

    @Override
    public String previewText() {
        return "Move " + looseFile + "\n  to " + destination();
    }

    @Override
    public void apply() throws IOException {
        Path destDir = looseFile.resolveSibling(skillName());
        Path dest = destDir.resolve("SKILL.md");
        SelfWriteTracker.markAboutToWrite(destDir);
        Files.createDirectories(destDir);
        SelfWriteTracker.markAboutToWrite(dest);
        Files.move(looseFile, dest);
        GitAutoCommitter.commitIfEnabled(dest);
        ChangeLogService.record(looseFile, ChangeKind.DELETED, ChangeSource.SELF);
        ChangeLogService.record(dest, ChangeKind.CREATED, ChangeSource.SELF);
    }
}

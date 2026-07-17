package com.ourgiant.kirocontrolpanel.steering;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A single steering doc: front-matter fields plus Markdown body. {@code workspaceRoot}
 * is null for a global (~/.kiro/steering) doc, or the pinned workspace root for a
 * workspace-scoped one.
 */
public class SteeringDoc {
    private final Path filePath;
    private final Path workspaceRoot;

    private String inclusion = "always";
    private List<String> fileMatchPatterns = new ArrayList<>();
    private String name = "";
    private String description = "";
    private String body = "";

    public SteeringDoc(Path filePath, Path workspaceRoot) {
        this.filePath = filePath;
        this.workspaceRoot = workspaceRoot;
    }

    public Path getFilePath() {
        return filePath;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public boolean isGlobal() {
        return workspaceRoot == null;
    }

    public String getFileName() {
        return filePath.getFileName().toString();
    }

    public String getInclusion() {
        return inclusion;
    }

    public void setInclusion(String inclusion) {
        this.inclusion = inclusion;
    }

    public List<String> getFileMatchPatterns() {
        return fileMatchPatterns;
    }

    public void setFileMatchPatterns(List<String> fileMatchPatterns) {
        this.fileMatchPatterns = fileMatchPatterns;
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}

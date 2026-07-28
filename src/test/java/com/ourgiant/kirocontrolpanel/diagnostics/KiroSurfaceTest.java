package com.ourgiant.kirocontrolpanel.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiroSurfaceTest {

    @TempDir
    Path root;

    @Test
    void classifiesMcpPath() {
        assertEquals(Optional.of(KiroSurface.MCP),
            KiroSurface.classify(root.resolve(".kiro/settings/mcp.json")));
    }

    @Test
    void classifiesSteeringPath() {
        assertEquals(Optional.of(KiroSurface.STEERING),
            KiroSurface.classify(root.resolve(".kiro/steering/tech.md")));
    }

    @Test
    void classifiesSkillsPath() {
        assertEquals(Optional.of(KiroSurface.SKILLS),
            KiroSurface.classify(root.resolve(".kiro/skills/my-skill/SKILL.md")));
    }

    @Test
    void classifiesHooksPath() {
        assertEquals(Optional.of(KiroSurface.HOOKS),
            KiroSurface.classify(root.resolve(".kiro/hooks/lint.json")));
    }

    @Test
    void classifiesAgentsPath() {
        assertEquals(Optional.of(KiroSurface.AGENTS),
            KiroSurface.classify(root.resolve(".kiro/agents/reviewer.json")));
    }

    @Test
    void unrecognizedPathIsEmpty() {
        assertTrue(KiroSurface.classify(root.resolve(".kiro/.gitignore")).isEmpty());
        assertTrue(KiroSurface.classify(root.resolve("not-under-kiro.txt")).isEmpty());
    }

    @Test
    void pathWithoutKiroSegmentIsEmpty() {
        assertTrue(KiroSurface.classify(root.resolve("some/other/file.txt")).isEmpty());
    }
}

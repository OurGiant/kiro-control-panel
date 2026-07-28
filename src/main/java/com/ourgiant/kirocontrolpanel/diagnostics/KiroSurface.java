package com.ourgiant.kirocontrolpanel.diagnostics;

import java.nio.file.Path;
import java.util.Optional;

/** Which of the app's five config surfaces a {@link Finding} belongs to. */
public enum KiroSurface {
    MCP,
    STEERING,
    SKILLS,
    HOOKS,
    AGENTS;

    /**
     * Classifies an arbitrary path under a {@code .kiro} tree by looking at the directory
     * name immediately following {@code .kiro} ({@code settings/} -> MCP, {@code steering/}
     * -> STEERING, etc.). Empty for anything not under a recognized surface's own
     * subdirectory (e.g. {@code .git}, a stray file directly in {@code .kiro/}). Shared
     * beyond this package -- the single source of truth for path-to-surface classification,
     * used by both the setup scanner and the change-log feature (issue #79).
     */
    public static Optional<KiroSurface> classify(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (int i = 0; i < normalized.getNameCount(); i++) {
            if (".kiro".equals(normalized.getName(i).toString())) {
                if (i + 1 >= normalized.getNameCount()) {
                    return Optional.empty();
                }
                String next = normalized.getName(i + 1).toString();
                return switch (next) {
                    case "settings" -> Optional.of(MCP);
                    case "steering" -> Optional.of(STEERING);
                    case "skills" -> Optional.of(SKILLS);
                    case "hooks" -> Optional.of(HOOKS);
                    case "agents" -> Optional.of(AGENTS);
                    default -> Optional.empty();
                };
            }
        }
        return Optional.empty();
    }
}

package com.ourgiant.kirocontrolpanel.migration;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A point-in-time read of where this machine stands on the kiro-cli V2 to V3 move (issue #147).
 *
 * @param cliFound              false if {@code kiro-cli} couldn't be run at all
 * @param cliVersion            e.g. {@code "2.16.2"}, or null if it couldn't be read
 * @param savedEngine           the saved {@code chat.agentEngine} setting ({@code "v2"}/{@code "v3"}),
 *                              or null when the user has never chosen one
 * @param agentUpgradeAvailable whether this kiro-cli has {@code agent upgrade}, the command that
 *                              converts V2 agent configs. Older 2.x builds don't, so they need
 *                              updating first
 * @param v2SessionCount        sessions in the V2 store ({@code ~/.kiro/sessions/cli})
 * @param v3SessionCount        sessions in the V3 store ({@code ~/.kiro/sessions/<hash>/sess_*})
 * @param legacyAgentFiles      agent configs V3 will skip until they're upgraded
 */
public record KiroCliV3Status(
    boolean cliFound,
    String cliVersion,
    String savedEngine,
    boolean agentUpgradeAvailable,
    int v2SessionCount,
    int v3SessionCount,
    List<Path> legacyAgentFiles
) {
    public static KiroCliV3Status notInstalled() {
        return new KiroCliV3Status(false, null, null, false, 0, 0, List.of());
    }

    public boolean v3IsDefault() {
        return "v3".equals(savedEngine);
    }

    /** The folders {@code kiro-cli agent upgrade <dir>} needs to be run against. */
    public Set<Path> legacyAgentDirs() {
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path file : legacyAgentFiles) {
            dirs.add(file.getParent());
        }
        return dirs;
    }

    /**
     * True when it's worth suggesting the move: V3 isn't the saved default yet, and there's
     * something of the user's that the switch affects -- agents V3 would skip, or V2 history
     * with no sign they've started using V3 already.
     */
    public boolean shouldSuggestMigration() {
        if (!cliFound || v3IsDefault()) {
            return false;
        }
        return !legacyAgentFiles.isEmpty() || (v2SessionCount > 0 && v3SessionCount == 0);
    }
}

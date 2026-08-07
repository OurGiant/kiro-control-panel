package com.ourgiant.kirocontrolpanel.sessions;

import com.ourgiant.kirocontrolpanel.AppPreferences;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the Sessions index DB location. Mirrors
 * {@code changelog.ChangeLogService.defaultLogFile}'s exact precedence
 * shape: a system-property override for tests (wired via {@code pom.xml}'s
 * surefire config, so {@code mvn test} never touches the real user's real
 * index) takes priority, then the user's own Settings override, then the
 * app's default data directory -- never inside {@code ~/.kiro} itself (see
 * issue #117: writes there would self-trigger the external-change monitor
 * and get swept into snapshots).
 */
public final class SessionIndexPaths {
    private static final String TEST_OVERRIDE_PROPERTY = "kiro.control.panel.sessionsIndexFile";

    private SessionIndexPaths() {
    }

    public static Path defaultIndexFile(AppPreferences preferences) {
        String testOverride = System.getProperty(TEST_OVERRIDE_PROPERTY);
        if (testOverride != null && !testOverride.isBlank()) {
            return Paths.get(testOverride);
        }
        String userOverride = preferences.getSessionsIndexLocation();
        if (userOverride != null && !userOverride.isBlank()) {
            return Paths.get(userOverride);
        }
        return Paths.get(System.getProperty("user.home"), ".kiro-control-panel", "sessions-index.db");
    }
}

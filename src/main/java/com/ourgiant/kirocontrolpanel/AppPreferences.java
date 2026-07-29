package com.ourgiant.kirocontrolpanel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Local app state: recent/pinned workspaces, theme choice, window bounds.
 * Backed by {@link Preferences} since this app's own state is small enough
 * that a database would be overkill.
 */
public class AppPreferences {

    private static final String KEY_THEME = "theme";
    private static final String KEY_WORKSPACES = "workspaces";
    private static final String KEY_WINDOW_X = "windowX";
    private static final String KEY_WINDOW_Y = "windowY";
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";
    private static final String KEY_FIRST_RUN_COMPLETE = "firstRunComplete";
    private static final String KEY_SKIP_POWERSHELL_PROFILE = "skipPowerShellProfile";
    private static final String KEY_GIT_TRACKING_ENABLED = "gitTrackingEnabled";
    private static final String KEY_FOLDER_MONITOR_ALERTS_ENABLED = "folderMonitorAlertsEnabled";
    private static final String KEY_LAST_NOTIFIED_UPDATE_VERSION = "lastNotifiedUpdateVersion";
    private static final String KEY_SNAPSHOT_ENABLED = "snapshotEnabled";
    private static final String KEY_SNAPSHOT_DESTINATION = "snapshotDestination";
    private static final String KEY_SNAPSHOT_FREQUENCY_MINUTES = "snapshotFrequencyMinutes";
    private static final String KEY_SNAPSHOT_RETENTION_COUNT = "snapshotRetentionCount";

    private static final String WORKSPACE_DELIMITER = "\n";
    private static final String DEFAULT_THEME = "GitHub Dark";
    private static final int DEFAULT_SNAPSHOT_FREQUENCY_MINUTES = 1440;
    private static final int DEFAULT_SNAPSHOT_RETENTION_COUNT = 30;

    private final Preferences prefs;

    public AppPreferences() {
        this.prefs = Preferences.userNodeForPackage(AppPreferences.class);
    }

    public String getTheme() {
        return prefs.get(KEY_THEME, DEFAULT_THEME);
    }

    public void setTheme(String themeName) {
        prefs.put(KEY_THEME, themeName);
    }

    public Set<String> getWorkspaces() {
        String joined = prefs.get(KEY_WORKSPACES, "");
        Set<String> workspaces = new LinkedHashSet<>();
        if (!joined.isBlank()) {
            for (String path : joined.split(WORKSPACE_DELIMITER)) {
                if (!path.isBlank()) {
                    workspaces.add(path);
                }
            }
        }
        return workspaces;
    }

    public void addWorkspace(String absolutePath) {
        Set<String> workspaces = getWorkspaces();
        workspaces.add(absolutePath);
        saveWorkspaces(workspaces);
    }

    public void removeWorkspace(String absolutePath) {
        Set<String> workspaces = getWorkspaces();
        workspaces.remove(absolutePath);
        saveWorkspaces(workspaces);
    }

    private void saveWorkspaces(Set<String> workspaces) {
        prefs.put(KEY_WORKSPACES, String.join(WORKSPACE_DELIMITER, workspaces));
    }

    public int[] getWindowBounds(int defaultWidth, int defaultHeight) {
        int width = prefs.getInt(KEY_WINDOW_WIDTH, defaultWidth);
        int height = prefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight);
        int x = prefs.getInt(KEY_WINDOW_X, Integer.MIN_VALUE);
        int y = prefs.getInt(KEY_WINDOW_Y, Integer.MIN_VALUE);
        return new int[] {x, y, width, height};
    }

    public void setWindowBounds(int x, int y, int width, int height) {
        prefs.putInt(KEY_WINDOW_X, x);
        prefs.putInt(KEY_WINDOW_Y, y);
        prefs.putInt(KEY_WINDOW_WIDTH, width);
        prefs.putInt(KEY_WINDOW_HEIGHT, height);
    }

    public boolean isFirstRunComplete() {
        return prefs.getBoolean(KEY_FIRST_RUN_COMPLETE, false);
    }

    public void setFirstRunComplete(boolean complete) {
        prefs.putBoolean(KEY_FIRST_RUN_COMPLETE, complete);
    }

    /**
     * On by default for launch speed; users whose $PROFILE sets up PATH/env vars kiro-cli
     * depends on can uncheck it in Config.
     */
    public boolean isSkipPowerShellProfile() {
        return prefs.getBoolean(KEY_SKIP_POWERSHELL_PROFILE, true);
    }

    public void setSkipPowerShellProfile(boolean skip) {
        prefs.putBoolean(KEY_SKIP_POWERSHELL_PROFILE, skip);
    }

    /** Off by default -- opt-in, since enabling it writes a git repo into the global ~/.kiro folder. */
    public boolean isGitTrackingEnabled() {
        return prefs.getBoolean(KEY_GIT_TRACKING_ENABLED, false);
    }

    public void setGitTrackingEnabled(boolean enabled) {
        prefs.putBoolean(KEY_GIT_TRACKING_ENABLED, enabled);
    }

    /**
     * On by default: Kiro reads everything under the global ~/.kiro tree as instructions, so a
     * heads-up on unexpected external changes is the safer default. Users who find it noisy
     * (or who trust what's about to change, e.g. before a Kiro session that edits skills) can
     * turn it off in Settings -- see also the session-only "Pause Change Alerts" File menu item
     * for a lighter-weight alternative that doesn't require remembering to turn this back on.
     */
    public boolean isFolderMonitorAlertsEnabled() {
        return prefs.getBoolean(KEY_FOLDER_MONITOR_ALERTS_ENABLED, true);
    }

    public void setFolderMonitorAlertsEnabled(boolean enabled) {
        prefs.putBoolean(KEY_FOLDER_MONITOR_ALERTS_ENABLED, enabled);
    }

    /**
     * The version the silent startup update check last auto-opened the About box for, so it
     * doesn't nag on every single launch while a known update sits unapplied -- once per new
     * version, not once per launch. Empty string if never notified. See #68.
     */
    public String getLastNotifiedUpdateVersion() {
        return prefs.get(KEY_LAST_NOTIFIED_UPDATE_VERSION, "");
    }

    public void setLastNotifiedUpdateVersion(String version) {
        prefs.put(KEY_LAST_NOTIFIED_UPDATE_VERSION, version);
    }

    /** Off by default -- opt-in, since enabling it writes snapshot archives to a chosen folder. See #91. */
    public boolean isSnapshotEnabled() {
        return prefs.getBoolean(KEY_SNAPSHOT_ENABLED, false);
    }

    public void setSnapshotEnabled(boolean enabled) {
        prefs.putBoolean(KEY_SNAPSHOT_ENABLED, enabled);
    }

    /** Empty string if the user hasn't chosen a destination folder yet. */
    public String getSnapshotDestination() {
        return prefs.get(KEY_SNAPSHOT_DESTINATION, "");
    }

    public void setSnapshotDestination(String absolutePath) {
        prefs.put(KEY_SNAPSHOT_DESTINATION, absolutePath);
    }

    public int getSnapshotFrequencyMinutes() {
        return prefs.getInt(KEY_SNAPSHOT_FREQUENCY_MINUTES, DEFAULT_SNAPSHOT_FREQUENCY_MINUTES);
    }

    public void setSnapshotFrequencyMinutes(int minutes) {
        prefs.putInt(KEY_SNAPSHOT_FREQUENCY_MINUTES, minutes);
    }

    public int getSnapshotRetentionCount() {
        return prefs.getInt(KEY_SNAPSHOT_RETENTION_COUNT, DEFAULT_SNAPSHOT_RETENTION_COUNT);
    }

    public void setSnapshotRetentionCount(int count) {
        prefs.putInt(KEY_SNAPSHOT_RETENTION_COUNT, count);
    }
}

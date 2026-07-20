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

    private static final String WORKSPACE_DELIMITER = "\n";
    private static final String DEFAULT_THEME = "GitHub Dark";

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

    /** Off by default: a user's $PROFILE may set PATH/env vars kiro-cli itself depends on. */
    public boolean isSkipPowerShellProfile() {
        return prefs.getBoolean(KEY_SKIP_POWERSHELL_PROFILE, false);
    }

    public void setSkipPowerShellProfile(boolean skip) {
        prefs.putBoolean(KEY_SKIP_POWERSHELL_PROFILE, skip);
    }
}

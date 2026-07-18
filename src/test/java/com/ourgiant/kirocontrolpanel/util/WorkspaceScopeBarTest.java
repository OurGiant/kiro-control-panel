package com.ourgiant.kirocontrolpanel.util;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies WorkspaceScopeBar instances stay in sync with each other via
 * WorkspaceRegistry — the bug this was written to catch: pinning a
 * workspace in one panel silently not showing up in another panel's scope
 * bar. JDialog can't be constructed headlessly (confirmed separately), but
 * WorkspaceScopeBar is a plain JPanel, so this runs fine without a display.
 */
class WorkspaceScopeBarTest {

    private AppPreferences preferences;
    private Set<String> originalWorkspaces;

    @BeforeEach
    void setUp() {
        preferences = new AppPreferences();
        originalWorkspaces = new LinkedHashSet<>(preferences.getWorkspaces());
    }

    @AfterEach
    void tearDown() {
        for (String workspace : new ArrayList<>(preferences.getWorkspaces())) {
            if (!originalWorkspaces.contains(workspace)) {
                preferences.removeWorkspace(workspace);
            }
        }
        WorkspaceRegistry.notifyChanged();
    }

    @Test
    void pinningWorkspaceInOneBarIsReflectedInSiblingBar() {
        WorkspaceScopeBar barA = new WorkspaceScopeBar(preferences);
        WorkspaceScopeBar barB = new WorkspaceScopeBar(preferences);
        String testWorkspace = "/tmp/workspace-scope-bar-test-" + System.nanoTime();

        preferences.addWorkspace(testWorkspace);
        WorkspaceRegistry.notifyChanged();

        assertTrue(containsLabel(barA, testWorkspace), "originating bar should see the new workspace");
        assertTrue(containsLabel(barB, testWorkspace), "sibling bar should see the new workspace too");
    }

    @Test
    void removingWorkspaceInOneBarIsReflectedInSiblingBar() {
        String testWorkspace = "/tmp/workspace-scope-bar-test-" + System.nanoTime();
        preferences.addWorkspace(testWorkspace);
        WorkspaceRegistry.notifyChanged();

        WorkspaceScopeBar barA = new WorkspaceScopeBar(preferences);
        WorkspaceScopeBar barB = new WorkspaceScopeBar(preferences);
        assertTrue(containsLabel(barA, testWorkspace));
        assertTrue(containsLabel(barB, testWorkspace));

        preferences.removeWorkspace(testWorkspace);
        WorkspaceRegistry.notifyChanged();

        assertFalse(containsLabel(barA, testWorkspace), "originating bar should drop the removed workspace");
        assertFalse(containsLabel(barB, testWorkspace), "sibling bar should drop the removed workspace too");
    }

    @Test
    void siblingBarKeepsItsOwnSelectionWhenAnotherWorkspaceIsPinnedElsewhere() {
        String existingWorkspace = "/tmp/workspace-scope-bar-test-existing-" + System.nanoTime();
        preferences.addWorkspace(existingWorkspace);
        WorkspaceRegistry.notifyChanged();

        WorkspaceScopeBar barB = new WorkspaceScopeBar(preferences);
        barB.getAvailableScopes().stream()
            .filter(scope -> existingWorkspace.equals(scope.label()))
            .findFirst()
            .ifPresent(barB::selectScope);
        WorkspaceScope selectedBefore = barB.getSelectedScope();

        String newWorkspace = "/tmp/workspace-scope-bar-test-new-" + System.nanoTime();
        preferences.addWorkspace(newWorkspace);
        WorkspaceRegistry.notifyChanged();

        assertTrue(containsLabel(barB, newWorkspace), "sibling bar should still learn about the new workspace");
        org.junit.jupiter.api.Assertions.assertEquals(selectedBefore, barB.getSelectedScope(),
            "a panel actively viewing one workspace shouldn't have its selection stolen by a pin happening elsewhere");
    }

    @Test
    void truncateForDisplayLeavesShortLabelsUnchanged() {
        org.junit.jupiter.api.Assertions.assertEquals("Global",
            WorkspaceScopeBar.truncateForDisplay("Global", 40));
        org.junit.jupiter.api.Assertions.assertEquals("/home/me/short-project",
            WorkspaceScopeBar.truncateForDisplay("/home/me/short-project", 40));
    }

    @Test
    void truncateForDisplayShortensLongLabelsKeepingTheTail() {
        String longPath = "/home/me/deeply/nested/path/that/goes/on/forever/my-actual-project";

        String truncated = WorkspaceScopeBar.truncateForDisplay(longPath, 40);

        org.junit.jupiter.api.Assertions.assertEquals(40, truncated.length());
        assertTrue(truncated.startsWith("..."), "should elide the (less useful) common-prefix segments");
        assertTrue(truncated.endsWith("my-actual-project"),
            "should keep the (more useful, distinguishing) tail of the path visible");
    }

    @Test
    void veryLongPinnedWorkspacePathDoesNotBalloonComboWidth() throws Exception {
        // Regression coverage: a very long pinned workspace path used to balloon
        // WorkspaceScopeBar's JComboBox (and so the whole bar) far past any
        // reasonable window width, since JComboBox sizes itself to its widest
        // rendered item and nothing capped the displayed text length.
        String longPath = "/tmp/" + "a".repeat(300) + "/workspace-scope-bar-test-" + System.nanoTime();
        preferences.addWorkspace(longPath);
        WorkspaceRegistry.notifyChanged();

        WorkspaceScopeBar bar = new WorkspaceScopeBar(preferences);
        bar.getAvailableScopes().stream()
            .filter(scope -> longPath.equals(scope.label()))
            .findFirst()
            .ifPresent(bar::selectScope);

        var field = WorkspaceScopeBar.class.getDeclaredField("scopeCombo");
        field.setAccessible(true);
        javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) field.get(bar);

        assertTrue(combo.getPreferredSize().width < 500,
            "combo width should stay bounded regardless of pinned workspace path length, was "
                + combo.getPreferredSize().width);
    }

    private static boolean containsLabel(WorkspaceScopeBar bar, String label) {
        List<WorkspaceScope> scopes = bar.getAvailableScopes();
        return scopes.stream().anyMatch(scope -> label.equals(scope.label()));
    }
}

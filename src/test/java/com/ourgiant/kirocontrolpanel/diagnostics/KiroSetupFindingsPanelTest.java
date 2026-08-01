package com.ourgiant.kirocontrolpanel.diagnostics;

import com.ourgiant.kirocontrolpanel.WorkspaceScope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code JDialog} throws {@code HeadlessException} in this project's build
 * container, but a plain {@code JPanel} constructs fine -- same reason
 * {@code KiroSetupFindingsPanel} is split out from {@code KiroSetupScanDialog}
 * (matches {@code HookFormPanel}/{@code HookEditDialog}). The Fix button's
 * confirm dialog itself isn't exercised here for the same reason -- see
 * {@link KiroSetupFindingsPanel#applyFix}'s javadoc.
 */
class KiroSetupFindingsPanelTest {

    private static final Path SOME_PATH = Paths.get("dummy.json");
    private static final WorkspaceScope SCOPE = new WorkspaceScope("test", Paths.get("/tmp/workspace"));

    private Finding notFixable() {
        return new Finding(KiroSurface.MCP, SCOPE, FindingSeverity.INVALID, SOME_PATH, "bad json", null);
    }

    private Finding fixable(Fix fix) {
        return new Finding(KiroSurface.SKILLS, SCOPE, FindingSeverity.STRUCTURAL, SOME_PATH, "loose file", fix);
    }

    @Test
    void emptyFindingsShowsCleanStatusAndDisablesButtons() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();

        panel.setFindings(List.of());

        assertTrue(panel.getFindings().isEmpty());
        assertFalse(panel.getOpenFileButton().isEnabled());
        assertFalse(panel.getFixButton().isEnabled());
        assertFalse(panel.getFixAllButton().isEnabled());
        assertTrue(panel.getRescanButton().isEnabled(), "Rescan should always be available, regardless of selection");
    }

    @Test
    void rescanButtonStaysEnabledRegardlessOfSelection() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        Finding finding = notFixable();
        panel.setFindings(List.of(finding));

        assertTrue(panel.getRescanButton().isEnabled());
        panel.selectFinding(finding);
        assertTrue(panel.getRescanButton().isEnabled());
    }

    @Test
    void clickingRescanInvokesTheRegisteredListenerAndRefreshesFindings() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        Finding stale = notFixable();
        panel.setFindings(List.of(stale));
        AtomicBoolean rescanned = new AtomicBoolean(false);
        panel.addRescanListener(() -> {
            rescanned.set(true);
            panel.setFindings(List.of());
        });

        panel.getRescanButton().doClick();

        assertTrue(rescanned.get());
        assertTrue(panel.getFindings().isEmpty(), "rescan should have replaced the stale finding list");
    }

    @Test
    void selectingNonFixableFindingEnablesOnlyOpenFile() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        Finding finding = notFixable();
        panel.setFindings(List.of(finding));

        panel.selectFinding(finding);

        assertTrue(panel.getOpenFileButton().isEnabled());
        assertFalse(panel.getFixButton().isEnabled());
    }

    @Test
    void selectingFixableFindingEnablesBothButtons() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        Finding finding = fixable(new NoOpFix());
        panel.setFindings(List.of(finding));

        panel.selectFinding(finding);

        assertTrue(panel.getOpenFileButton().isEnabled());
        assertTrue(panel.getFixButton().isEnabled());
    }

    @Test
    void fixAllButtonEnabledWheneverAnyFindingIsFixableRegardlessOfSelection() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        panel.setFindings(List.of(notFixable(), fixable(new NoOpFix())));

        assertTrue(panel.getFixAllButton().isEnabled(),
            "Fix All should be available whenever at least one finding is fixable, with nothing selected");
    }

    @Test
    void fixAllButtonDisabledWhenNoFindingIsFixable() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        panel.setFindings(List.of(notFixable(), notFixable()));

        assertFalse(panel.getFixAllButton().isEnabled());
    }

    @Test
    void applyFixRemovesFindingOnSuccess() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        AtomicBoolean applied = new AtomicBoolean(false);
        Finding finding = fixable(new Fix() {
            @Override
            public String previewText() {
                return "no-op";
            }

            @Override
            public void apply() {
                applied.set(true);
            }
        });
        panel.setFindings(List.of(finding));

        boolean result = panel.applyFix(finding);

        assertTrue(result);
        assertTrue(applied.get());
        assertTrue(panel.getFindings().isEmpty());
    }

    // applyFix's failure path (IOException from Fix.apply()) shows a JOptionPane error
    // dialog, which throws HeadlessException in this project's build container -- same
    // known limitation as the invalid-JSON confirm dialogs elsewhere in this app. Not
    // unit tested here; covered only by manual verification.

    @Test
    void applyAllFixesAppliesEveryFixableFindingAndLeavesNonFixableOnesAlone() {
        KiroSetupFindingsPanel panel = new KiroSetupFindingsPanel();
        AtomicBoolean firstApplied = new AtomicBoolean(false);
        AtomicBoolean secondApplied = new AtomicBoolean(false);
        Finding first = fixable(new Fix() {
            @Override
            public String previewText() {
                return "first";
            }

            @Override
            public void apply() {
                firstApplied.set(true);
            }
        });
        Finding second = fixable(new Fix() {
            @Override
            public String previewText() {
                return "second";
            }

            @Override
            public void apply() {
                secondApplied.set(true);
            }
        });
        Finding stale = notFixable();
        panel.setFindings(List.of(first, second, stale));

        int failed = panel.applyAllFixes(List.of(first, second));

        assertEquals(0, failed);
        assertTrue(firstApplied.get());
        assertTrue(secondApplied.get());
        assertEquals(List.of(stale), panel.getFindings());
        assertFalse(panel.getFixAllButton().isEnabled(), "no fixable findings remain after Fix All");
    }

    // applyAllFixes's failure-summary path (one or more Fix.apply() calls throwing IOException)
    // shows a JOptionPane warning dialog, same HeadlessException limitation as applyFix's
    // failure path above. Not unit tested here; covered only by manual verification.

    /** A {@link Fix} whose {@code apply()} never throws, for tests that don't care about the failure path. */
    private static final class NoOpFix implements Fix {
        @Override
        public String previewText() {
            return "no-op";
        }

        @Override
        public void apply() {
        }
    }
}

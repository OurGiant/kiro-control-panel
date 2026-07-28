package com.ourgiant.kirocontrolpanel.testsupport;

import com.ourgiant.kirocontrolpanel.AppPreferences;
import com.ourgiant.kirocontrolpanel.agents.AgentFormPanel;
import com.ourgiant.kirocontrolpanel.agents.AgentsPanel;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogPanel;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSetupFindingsPanel;
import com.ourgiant.kirocontrolpanel.hooks.HookFormPanel;
import com.ourgiant.kirocontrolpanel.hooks.HooksPanel;
import com.ourgiant.kirocontrolpanel.mcp.McpPanel;
import com.ourgiant.kirocontrolpanel.mcp.McpServerFormPanel;
import com.ourgiant.kirocontrolpanel.skills.SkillsPanel;
import com.ourgiant.kirocontrolpanel.steering.SteeringPanel;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.JComponent;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Regression suite for the bug class behind issue #18 (WorkspaceScopeBar
 * clipped at a narrow window width, shipped in 1.1.0): every JPanel-rooted
 * component MainWindow can show, checked for clipped children at both the
 * app's real default size and a deliberately narrow one. Deliberately
 * excludes the 12 JDialog/JFrame classes -- Window construction itself is
 * what's blocked headlessly in this project's CI, not layout computation;
 * those stay covered only by the manual verify/verify-java-swing process.
 * <p>
 * Also excludes {@code UsagePanel}, for the same category of reason: its
 * JEditorPane hits a known, pre-existing JDK bug in Swing's HTML flow layout
 * (a NullPointerException deep in FlowView$FlowStrategy on a null
 * viewBuffer) that's reproduced intermittently in this project's CI --
 * originally only at a pathologically narrow 300px width on macOS x64/Intel,
 * then recurring even after widening the tested width to 640px (matching
 * MainWindow's real enforced minimum) and even on macOS arm64, where it was
 * never previously observed. Not a bug in this app's code, and not
 * reliably reproducible enough to assert against headlessly; covered only
 * by the manual verify/verify-java-swing process instead.
 */
class PanelLayoutClippingTest {

    private static final int DEFAULT_WIDTH = 900;
    private static final int DEFAULT_HEIGHT = 600;
    // Matches the diagnostic harness width that originally found and confirmed issue #18.
    private static final int NARROW_WIDTH = 300;

    private static DirectoryWatcher watcher;

    @BeforeAll
    static void setUp() throws IOException {
        // One shared instance for the whole class: no close()/shutdown exists on
        // DirectoryWatcher in this codebase, and nothing here registers a directory
        // or cares about watch behavior, so 9 separate WatchService handles would
        // be pure overhead.
        watcher = new DirectoryWatcher();
    }

    static Stream<Arguments> panels() {
        AppPreferences preferences = new AppPreferences();
        return Stream.of(
            Arguments.of("McpPanel", (Supplier<JComponent>) () -> new McpPanel(preferences, watcher), NARROW_WIDTH),
            Arguments.of("SteeringPanel", (Supplier<JComponent>) () -> new SteeringPanel(preferences, watcher), NARROW_WIDTH),
            Arguments.of("SkillsPanel", (Supplier<JComponent>) () -> new SkillsPanel(preferences, watcher), NARROW_WIDTH),
            Arguments.of("HooksPanel", (Supplier<JComponent>) () -> new HooksPanel(preferences, watcher), NARROW_WIDTH),
            Arguments.of("AgentsPanel", (Supplier<JComponent>) () -> new AgentsPanel(preferences, watcher), NARROW_WIDTH),
            Arguments.of("McpServerFormPanel", (Supplier<JComponent>) McpServerFormPanel::new, NARROW_WIDTH),
            Arguments.of("HookFormPanel", (Supplier<JComponent>) HookFormPanel::new, NARROW_WIDTH),
            Arguments.of("AgentFormPanel", (Supplier<JComponent>) AgentFormPanel::new, NARROW_WIDTH),
            Arguments.of("KiroSetupFindingsPanel", (Supplier<JComponent>) KiroSetupFindingsPanel::new, NARROW_WIDTH),
            Arguments.of("ChangeLogPanel", (Supplier<JComponent>) ChangeLogPanel::new, NARROW_WIDTH)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("panels")
    void doesNotClipChildrenAtAnyTestedSize(String name, Supplier<JComponent> factory, int narrowWidth) {
        JComponent panel = factory.get();

        // Same component reused across both sizes, resized in place -- matches
        // the real-world scenario (an already-open window being resized narrower).
        assertAll(
            () -> LayoutAssertions.assertNoClippedChildren(panel, DEFAULT_WIDTH, DEFAULT_HEIGHT),
            () -> LayoutAssertions.assertNoClippedChildren(panel, narrowWidth, DEFAULT_HEIGHT)
        );
    }
}

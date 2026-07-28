package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.agents.AgentsPanel;
import com.ourgiant.kirocontrolpanel.changelog.ChangeLogDialog;
import com.ourgiant.kirocontrolpanel.diagnostics.Finding;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSetupScanDialog;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSetupScanner;
import com.ourgiant.kirocontrolpanel.hooks.HooksPanel;
import com.ourgiant.kirocontrolpanel.mcp.McpPanel;
import com.ourgiant.kirocontrolpanel.skills.SkillsPanel;
import com.ourgiant.kirocontrolpanel.steering.SteeringPanel;
import com.ourgiant.kirocontrolpanel.usage.UsagePanel;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.IconFactory;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Main tabbed window. Normally hidden behind the tray icon; TrayApp owns the
 * show/hide/exit lifecycle.
 */
public class MainWindow extends JFrame {
    private static final Dimension MINIMUM_SIZE = new Dimension(640, 480);

    private final AppPreferences preferences;
    private Runnable quitHandler = () -> System.exit(0);
    private Consumer<Boolean> alertsPauseHandler = paused -> { };

    public MainWindow(AppPreferences preferences, DirectoryWatcher watcher) {
        super("Kiro Control Panel");
        this.preferences = preferences;

        setIconImage(IconFactory.createAppIcon(64));
        setJMenuBar(createMenuBar());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("MCP Servers", new McpPanel(preferences, watcher));
        tabs.addTab("Steering", new SteeringPanel(preferences, watcher));
        tabs.addTab("Skills", new SkillsPanel(preferences, watcher));
        tabs.addTab("Hooks", new HooksPanel(preferences, watcher));
        tabs.addTab("Agents", new AgentsPanel(preferences, watcher));
        tabs.addTab("Usage", new UsagePanel());
        setContentPane(tabs);

        // Below this, content isn't just visually cramped -- some platforms'
        // JDK/Swing HTML text layout (JEditorPane, used by the Usage tab)
        // can throw a NullPointerException deep in FlowView$FlowStrategy at
        // pathologically narrow effective widths (a known font-metrics-
        // dependent JDK bug, reproduced on macOS x64/Intel CI but not Linux
        // -- see PanelLayoutClippingTest). setMinimumSize alone only
        // constrains interactive resizing, not a stale narrow size restored
        // from a previous session, so the restored bounds are clamped too.
        setMinimumSize(MINIMUM_SIZE);

        int[] bounds = preferences.getWindowBounds(900, 600);
        setSize(Math.max(bounds[2], MINIMUM_SIZE.width), Math.max(bounds[3], MINIMUM_SIZE.height));
        if (bounds[0] != Integer.MIN_VALUE) {
            setLocation(bounds[0], bounds[1]);
        } else {
            setLocationRelativeTo(null);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.setMnemonic(KeyEvent.VK_S);
        settingsItem.addActionListener(e -> new SettingsDialog(this, preferences).setVisible(true));
        fileMenu.add(settingsItem);

        fileMenu.addSeparator();

        // Session-only, deliberately not persisted to AppPreferences: for "I know a Kiro
        // session is about to touch these files, don't interrupt me for a while" without
        // having to remember to flip a permanent setting back afterward. The permanent
        // on/off toggle lives in Settings instead. See #66.
        JCheckBoxMenuItem pauseAlertsItem = new JCheckBoxMenuItem("Pause Change Alerts");
        pauseAlertsItem.addActionListener(e -> alertsPauseHandler.accept(pauseAlertsItem.isSelected()));
        fileMenu.add(pauseAlertsItem);

        fileMenu.addSeparator();

        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.setMnemonic(KeyEvent.VK_Q);
        quitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        quitItem.addActionListener(e -> quitHandler.run());
        fileMenu.add(quitItem);

        return fileMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem viewLogsItem = new JMenuItem("View Logs...");
        viewLogsItem.addActionListener(e -> new LogViewerDialog(this).setVisible(true));
        helpMenu.add(viewLogsItem);

        JMenuItem scanSetupItem = new JMenuItem("Scan Kiro Setup...");
        scanSetupItem.addActionListener(e -> runSetupScan());
        helpMenu.add(scanSetupItem);

        JMenuItem changeLogItem = new JMenuItem("View Change Log...");
        changeLogItem.addActionListener(e -> new ChangeLogDialog(this).setVisible(true));
        helpMenu.add(changeLogItem);

        helpMenu.addSeparator();

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    private void runSetupScan() {
        new KiroSetupScanDialog(this, scanForFindings(preferences)).setVisible(true);
    }

    /** Also used by TrayApp for the silent first-run scan. */
    public static List<Finding> scanForFindings(AppPreferences preferences) {
        List<WorkspaceScope> scopes = new ArrayList<>();
        scopes.add(WorkspaceScope.global());
        scopes.addAll(WorkspaceScope.pinnedWorkspaces(preferences));
        return new KiroSetupScanner().scan(scopes);
    }

    /** TrayApp wires this to its own exit logic (tray icon cleanup, System.exit). */
    public void setQuitHandler(Runnable quitHandler) {
        this.quitHandler = quitHandler;
    }

    /** TrayApp wires this to gate its external-change notification on the pause state. */
    public void setAlertsPauseHandler(Consumer<Boolean> alertsPauseHandler) {
        this.alertsPauseHandler = alertsPauseHandler;
    }

    public void persistWindowBounds() {
        preferences.setWindowBounds(getX(), getY(), getWidth(), getHeight());
    }
}

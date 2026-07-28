package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.diagnostics.Finding;
import com.ourgiant.kirocontrolpanel.diagnostics.KiroSetupScanDialog;
import com.ourgiant.kirocontrolpanel.util.AppVersion;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.GlobalKiroFolderMonitor;
import com.ourgiant.kirocontrolpanel.util.IconFactory;
import com.ourgiant.kirocontrolpanel.util.KiroSessionLauncher;
import com.ourgiant.kirocontrolpanel.util.NotificationThrottle;
import com.ourgiant.kirocontrolpanel.util.ProcessDetacher;
import com.ourgiant.kirocontrolpanel.util.UpdateChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.desktop.AppReopenedListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Entry point. Boots the tray icon and keeps the app resident there; the
 * main window is shown/hidden on demand rather than being the primary
 * lifecycle owner.
 */
public class TrayApp {
    private static final Logger logger = LoggerFactory.getLogger(TrayApp.class);
    private static final String TOOLTIP = "Kiro Control Panel";
    // A single external editing session (e.g. open a file in vim, save it a few times, quit)
    // can trip the folder monitor several times, each several seconds apart -- well outside
    // its 1s debounce window. Every change is still logged at INFO regardless; this only
    // throttles how often the user gets interrupted with a popup they have to dismiss. See #62.
    private static final Duration EXTERNAL_CHANGE_NOTIFICATION_COOLDOWN = Duration.ofSeconds(60);

    private final AppPreferences preferences = new AppPreferences();
    private final NotificationThrottle externalChangeNotificationThrottle =
        new NotificationThrottle(EXTERNAL_CHANGE_NOTIFICATION_COOLDOWN);
    private MainWindow mainWindow;
    private TrayIcon trayIcon;
    private boolean dockReopenSupported;
    // Session-only (see MainWindow's "Pause Change Alerts" File menu item, #66) -- deliberately
    // not persisted to AppPreferences, unlike isFolderMonitorAlertsEnabled().
    private boolean alertsPausedForSession;

    public static void main(String[] args) {
        // Must be set before the first HttpClient/ProxySelector use (catalog refresh,
        // update check): without it, java.net's default ProxySelector ignores the
        // OS/registry proxy config entirely, which silently breaks outbound HTTPS on
        // networks that require a system proxy (e.g. many managed Windows machines) -- see #35.
        System.setProperty("java.net.useSystemProxies", "true");
        if (ProcessDetacher.relaunchDetached(args)) {
            return;
        }
        SwingUtilities.invokeLater(() -> new TrayApp().start());
    }

    private void start() {
        ThemeManager.applyTheme(preferences.getTheme());

        DirectoryWatcher watcher;
        try {
            watcher = new DirectoryWatcher();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start file watcher", e);
        }

        mainWindow = new MainWindow(preferences, watcher);
        mainWindow.setQuitHandler(this::exitApplication);
        mainWindow.setAlertsPauseHandler(paused -> alertsPausedForSession = paused);
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (trayIcon != null || dockReopenSupported) {
                    mainWindow.persistWindowBounds();
                    mainWindow.setVisible(false);
                } else {
                    exitApplication();
                }
            }
        });

        dockReopenSupported = installDockReopenHandler();
        initializeSystemTray();
        startGlobalKiroFolderMonitor();
        mainWindow.setVisible(true);
        syncWindowPositionWithWindowManager(mainWindow);

        if (!preferences.isFirstRunComplete()) {
            new FirstRunDialog(mainWindow).setVisible(true);
            preferences.setFirstRunComplete(true);
            runFirstRunSetupScan();
        }

        checkForUpdateAndNotifyIfNewer();
    }

    /**
     * Silent unless something was actually found: an existing Kiro installation with a
     * clean .kiro tree shows nothing here -- the same scan is always available afterward
     * via Help > Scan Kiro Setup..., so this is purely a "you might want to know" nudge
     * for new users whose existing files predate this app. See #78.
     */
    private void runFirstRunSetupScan() {
        List<Finding> findings = MainWindow.scanForFindings(preferences);
        if (!findings.isEmpty()) {
            new KiroSetupScanDialog(mainWindow, findings).setVisible(true);
        }
    }

    /**
     * Silent unless there's actually something to say: no UI at all if up to date or the
     * check fails, and only once per newly-released version (not once per launch) if
     * there's an update -- otherwise a known update sitting unapplied would pop this on
     * every single startup. A user can still always check manually via Help > About
     * regardless of this state. See #68.
     */
    private void checkForUpdateAndNotifyIfNewer() {
        SwingWorker<Optional<UpdateChecker.ReleaseInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<UpdateChecker.ReleaseInfo> doInBackground() {
                return UpdateChecker.fetchLatestRelease();
            }

            @Override
            protected void done() {
                Optional<UpdateChecker.ReleaseInfo> release;
                try {
                    release = get();
                } catch (Exception e) {
                    logger.warn("Silent startup update check failed", e);
                    return;
                }
                if (release.isEmpty()) {
                    return;
                }
                UpdateChecker.ReleaseInfo info = release.get();
                if (!UpdateChecker.isNewerVersion(info.version(), AppVersion.resolve())) {
                    return;
                }
                if (info.version().equals(preferences.getLastNotifiedUpdateVersion())) {
                    return;
                }
                preferences.setLastNotifiedUpdateVersion(info.version());
                new AboutDialog(mainWindow, info).setVisible(true);
            }
        };
        worker.execute();
    }

    /**
     * The Dock icon reopen handler (fired when the user clicks the running app's Dock
     * icon with no window open) is a macOS-only fallback for getting the main window
     * back, independent of the system tray -- the tray icon can be unreliable or lost
     * in a crowded menu bar, but the Dock icon is always present since we don't set
     * LSUIElement. AppReopenedListener has no isSupported() query (addAppEventListener
     * just silently no-ops on platforms that don't fire the event), so this is gated
     * on OS detection directly rather than an AWT capability check. See #46.
     */
    private boolean installDockReopenHandler() {
        if (KiroSessionLauncher.detect(System.getProperty("os.name")) != KiroSessionLauncher.Platform.MACOS
                || !Desktop.isDesktopSupported()) {
            return false;
        }
        AppReopenedListener listener = e -> restoreWindow();
        Desktop.getDesktop().addAppEventListener(listener);
        return true;
    }

    private void initializeSystemTray() {
        if (!SystemTray.isSupported()) {
            logger.info("System tray is not supported on this platform; window close will {}.",
                dockReopenSupported ? "hide the window (Dock icon click reopens it)" : "exit the app");
            mainWindow.setDefaultCloseOperation(dockReopenSupported ? JFrame.HIDE_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
            return;
        }

        try {
            Image trayImage = IconFactory.createAppIcon(24);

            PopupMenu trayMenu = new PopupMenu();
            MenuItem showItem = new MenuItem("Show");
            showItem.addActionListener(e -> restoreWindow());
            MenuItem launchTerminalItem = new MenuItem("Launch kiro-cli...");
            // Tray menu has no "current scope" concept the way WorkspaceScopeBar does
            // (nothing is "selected" here), so this always launches in the home directory.
            launchTerminalItem.addActionListener(e ->
                KiroSessionLauncher.launchSession(mainWindow, Paths.get(System.getProperty("user.home")),
                    preferences.isSkipPowerShellProfile()));
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> exitApplication());
            trayMenu.add(showItem);
            trayMenu.add(launchTerminalItem);
            trayMenu.addSeparator();
            trayMenu.add(exitItem);

            trayIcon = new TrayIcon(trayImage, TOOLTIP, trayMenu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> restoreWindow());
            SystemTray.getSystemTray().add(trayIcon);

            mainWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        } catch (AWTException e) {
            logger.warn("Failed to initialize system tray icon; window close will {}.",
                dockReopenSupported ? "hide the window (Dock icon click reopens it)" : "exit the app", e);
            trayIcon = null;
            mainWindow.setDefaultCloseOperation(dockReopenSupported ? JFrame.HIDE_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
        }
    }

    /**
     * Kiro reads everything under the global ~/.kiro tree (steering docs, skills, agents,
     * mcp.json) as instructions, and there are documented cases of adversaries poisoning
     * that content with malicious instructions -- so surface a heads-up when it changes,
     * even though most changes here are entirely legitimate (Kiro IDE itself, or this
     * app's own Global-scope saves). See #50.
     */
    private void startGlobalKiroFolderMonitor() {
        try {
            new GlobalKiroFolderMonitor(KiroPaths.globalKiroHome(), this::notifyGlobalKiroChanged);
        } catch (IOException e) {
            logger.warn("Failed to start global .kiro folder monitor", e);
        }
    }

    private void notifyGlobalKiroChanged() {
        logger.info("Detected a change under the global .kiro folder from outside this app");
        // Every change is logged above regardless, permanently disabled or paused or not --
        // the audit trail from #49/#50 stays intact either way, only the popup is gated.
        if (!preferences.isFolderMonitorAlertsEnabled() || alertsPausedForSession) {
            return;
        }
        if (trayIcon != null && externalChangeNotificationThrottle.tryAcquire()) {
            trayIcon.displayMessage("Kiro configuration changed",
                "Files under ~/.kiro (steering docs, skills, agents, or MCP servers) changed outside "
                    + "Kiro Control Panel. Worth a look if you weren't expecting it.",
                TrayIcon.MessageType.INFO);
        }
    }

    private void restoreWindow() {
        boolean wasVisible = mainWindow.isVisible();
        mainWindow.setVisible(true);
        mainWindow.setExtendedState(JFrame.NORMAL);
        mainWindow.toFront();
        mainWindow.requestFocus();
        if (!wasVisible) {
            // First real show after being hidden in the tray skips start()'s post-show fix;
            // apply it here too.
            syncWindowPositionWithWindowManager(mainWindow);
        }
    }

    private void exitApplication() {
        mainWindow.persistWindowBounds();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        System.exit(0);
    }

    /**
     * On Linux/X11 the window manager confirms a window's real on-screen position
     * asynchronously after setVisible(true); FlatLaf's heavyweight popups (e.g. our
     * Theme menu) compute their screen position from that value, so clicking a menu
     * before the confirmation lands can render the popup at (0,0). Manually moving
     * the window fixes it by forcing a fresh position round-trip — nudge it
     * programmatically right after showing it so the fix applies before the user
     * can click anything. Ported from aws-idp-saml-ui (#71/#70).
     */
    private static void syncWindowPositionWithWindowManager(Window window) {
        Point location = window.getLocation();
        window.setLocation(location.x + 1, location.y);
        window.setLocation(location.x, location.y);
    }
}

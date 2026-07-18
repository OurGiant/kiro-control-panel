package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.IconFactory;
import com.ourgiant.kirocontrolpanel.util.KiroSessionLauncher;
import com.ourgiant.kirocontrolpanel.util.ProcessDetacher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;

/**
 * Entry point. Boots the tray icon and keeps the app resident there; the
 * main window is shown/hidden on demand rather than being the primary
 * lifecycle owner.
 */
public class TrayApp {
    private static final Logger logger = LoggerFactory.getLogger(TrayApp.class);
    private static final String TOOLTIP = "Kiro Control Panel";

    private final AppPreferences preferences = new AppPreferences();
    private MainWindow mainWindow;
    private TrayIcon trayIcon;

    public static void main(String[] args) {
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
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (trayIcon != null) {
                    mainWindow.persistWindowBounds();
                    mainWindow.setVisible(false);
                } else {
                    exitApplication();
                }
            }
        });

        initializeSystemTray();
        mainWindow.setVisible(true);
        syncWindowPositionWithWindowManager(mainWindow);

        if (!preferences.isFirstRunComplete()) {
            new FirstRunDialog(mainWindow).setVisible(true);
            preferences.setFirstRunComplete(true);
        }
    }

    private void initializeSystemTray() {
        if (!SystemTray.isSupported()) {
            logger.info("System tray is not supported on this platform; window close will exit the app.");
            mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        try {
            Image trayImage = IconFactory.createAppIcon(24);

            PopupMenu trayMenu = new PopupMenu();
            MenuItem showItem = new MenuItem("Show");
            showItem.addActionListener(e -> restoreFromTray());
            MenuItem launchTerminalItem = new MenuItem("Launch kiro-cli...");
            // Tray menu has no "current scope" concept the way WorkspaceScopeBar does
            // (nothing is "selected" here), so this always launches in the home directory.
            launchTerminalItem.addActionListener(e ->
                KiroSessionLauncher.launchSession(mainWindow, Paths.get(System.getProperty("user.home"))));
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> exitApplication());
            trayMenu.add(showItem);
            trayMenu.add(launchTerminalItem);
            trayMenu.addSeparator();
            trayMenu.add(exitItem);

            trayIcon = new TrayIcon(trayImage, TOOLTIP, trayMenu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> restoreFromTray());
            SystemTray.getSystemTray().add(trayIcon);

            mainWindow.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        } catch (AWTException e) {
            logger.warn("Failed to initialize system tray icon; window close will exit the app.", e);
            trayIcon = null;
            mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
    }

    private void restoreFromTray() {
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

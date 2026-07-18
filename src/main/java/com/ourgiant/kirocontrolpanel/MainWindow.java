package com.ourgiant.kirocontrolpanel;

import com.ourgiant.kirocontrolpanel.hooks.HooksPanel;
import com.ourgiant.kirocontrolpanel.mcp.McpPanel;
import com.ourgiant.kirocontrolpanel.skills.SkillsPanel;
import com.ourgiant.kirocontrolpanel.steering.SteeringPanel;
import com.ourgiant.kirocontrolpanel.usage.UsagePanel;
import com.ourgiant.kirocontrolpanel.util.DirectoryWatcher;
import com.ourgiant.kirocontrolpanel.util.IconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Main tabbed window. Normally hidden behind the tray icon; TrayApp owns the
 * show/hide/exit lifecycle.
 */
public class MainWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    private final AppPreferences preferences;
    private Runnable quitHandler = () -> System.exit(0);

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
        tabs.addTab("Usage", new UsagePanel());
        setContentPane(tabs);

        int[] bounds = preferences.getWindowBounds(900, 600);
        setSize(bounds[2], bounds[3]);
        if (bounds[0] != Integer.MIN_VALUE) {
            setLocation(bounds[0], bounds[1]);
        } else {
            setLocationRelativeTo(null);
        }
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createConfigMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.setMnemonic(KeyEvent.VK_Q);
        quitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        quitItem.addActionListener(e -> quitHandler.run());
        fileMenu.add(quitItem);

        return fileMenu;
    }

    private JMenu createConfigMenu() {
        JMenu configMenu = new JMenu("Config");
        configMenu.setMnemonic(KeyEvent.VK_C);

        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        String currentTheme = preferences.getTheme();
        for (String themeName : ThemeManager.getAvailableThemeNames()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(themeName, themeName.equals(currentTheme));
            ActionListener listener = e -> {
                if (ThemeManager.applyTheme(themeName)) {
                    preferences.setTheme(themeName);
                } else {
                    logger.warn("Failed to apply theme {}", themeName);
                }
            };
            item.addActionListener(listener);
            themeGroup.add(item);
            themeMenu.add(item);
        }
        configMenu.add(themeMenu);

        return configMenu;
    }

    private JMenu createHelpMenu() {
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(aboutItem);

        return helpMenu;
    }

    /** TrayApp wires this to its own exit logic (tray icon cleanup, System.exit). */
    public void setQuitHandler(Runnable quitHandler) {
        this.quitHandler = quitHandler;
    }

    public void persistWindowBounds() {
        preferences.setWindowBounds(getX(), getY(), getWidth(), getHeight());
    }
}

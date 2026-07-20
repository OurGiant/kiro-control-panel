package com.ourgiant.kirocontrolpanel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Launches kiro-cli in a brand new, independent OS terminal window -- never
 * read, controlled, or driven by this app afterward, same spirit as
 * {@link DesktopUtils}'s "reveal in file manager" actions. Scoped to exactly
 * three terminals: macOS Terminal.app, Windows PowerShell 7 (pwsh.exe, not
 * the older powershell.exe), and Ubuntu's gnome-terminal -- other terminals
 * are out of scope for v1 (see issue #24).
 */
public final class KiroSessionLauncher {
    private static final Logger logger = LoggerFactory.getLogger(KiroSessionLauncher.class);
    private static final String GNOME_TERMINAL_BINARY = "gnome-terminal";
    private static final String PWSH_BINARY = "pwsh.exe";

    private KiroSessionLauncher() {
    }

    public enum Platform {
        MACOS, WINDOWS, LINUX, UNSUPPORTED
    }

    public static Platform detect(String osName) {
        if (osName == null) {
            return Platform.UNSUPPORTED;
        }
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("mac") || lower.contains("darwin")) {
            return Platform.MACOS;
        }
        if (lower.contains("win")) {
            return Platform.WINDOWS;
        }
        if (lower.contains("linux")) {
            return Platform.LINUX;
        }
        return Platform.UNSUPPORTED;
    }

    /** No shell involved on Linux -- ProcessBuilder passes argv directly, so the directory needs no escaping. */
    public static List<String> buildLinuxCommand(String directory) {
        return List.of(GNOME_TERMINAL_BINARY, "--working-directory=" + directory,
            "--", "bash", "-c", "kiro-cli; exec bash");
    }

    /**
     * "start" detaches pwsh into its own console with real console stdio (and
     * respects the user's default terminal app on Win 11, e.g. Windows Terminal).
     * Launching pwsh.exe directly wires its std handles to this JVM's pipes
     * instead, leaving the new console window blank/unresponsive -- see #36.
     * "Kiro Session" must be present: start treats the first quoted arg as the
     * window title, and ProcessBuilder quotes it for us since it contains a space.
     *
     * @param skipProfile adds -NoProfile, skipping the user's $PROFILE. On by default
     *                    (see AppPreferences#isSkipPowerShellProfile) for launch speed;
     *                    users whose profile sets up PATH/env vars kiro-cli depends on
     *                    can uncheck it -- see #42.
     */
    public static List<String> buildWindowsCommand(String directory, boolean skipProfile) {
        String script = "Set-Location -LiteralPath '" + escapePowerShellSingleQuoted(directory) + "'; kiro-cli";
        List<String> command = new ArrayList<>(List.of("cmd.exe", "/c", "start", "Kiro Session",
            PWSH_BINARY, "-NoExit"));
        if (skipProfile) {
            command.add("-NoProfile");
        }
        command.add("-Command");
        command.add(script);
        return command;
    }

    /** Two independent escaping layers: POSIX shell quoting, then AppleScript string-literal quoting of the result. */
    public static List<String> buildMacCommand(String directory) {
        String shellCommand = "cd '" + escapeShellSingleQuoted(directory) + "' && kiro-cli";
        String appleScriptLiteral = escapeAppleScriptDoubleQuoted(shellCommand);
        return List.of("osascript",
            "-e", "tell application \"Terminal\" to activate",
            "-e", "tell application \"Terminal\" to do script \"" + appleScriptLiteral + "\"");
    }

    /** POSIX single-quoted shell literal: close-quote, escaped-quote, reopen-quote. */
    public static String escapeShellSingleQuoted(String value) {
        return value.replace("'", "'\\''");
    }

    /** PowerShell single-quoted string literal: an embedded ' is escaped by doubling it. */
    public static String escapePowerShellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    /** AppleScript double-quoted string literal: backslash first, then quote -- order matters. */
    public static String escapeAppleScriptDoubleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Package-private, for tests: scans a PATH-style env value for an executable named {@code binaryName}. */
    static String findOnPath(String pathEnv, String binaryName, Predicate<File> isExecutable) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, binaryName);
            if (isExecutable.test(candidate)) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    public static void launchSession(Component parent, Path workingDirectory, boolean skipWindowsProfile) {
        if (workingDirectory == null || !Files.isDirectory(workingDirectory)) {
            JOptionPane.showMessageDialog(parent,
                "That location doesn't exist yet:\n" + workingDirectory, "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String directory = workingDirectory.toString();
        switch (detect(System.getProperty("os.name"))) {
            case LINUX -> launchLinux(parent, directory);
            case WINDOWS -> launchWindows(parent, directory, skipWindowsProfile);
            case MACOS -> launchMac(parent, directory);
            case UNSUPPORTED -> JOptionPane.showMessageDialog(parent,
                "Launching a terminal isn't supported on this platform.",
                "Not Supported", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void launchLinux(Component parent, String directory) {
        if (findOnPath(System.getenv("PATH"), GNOME_TERMINAL_BINARY, File::canExecute) == null) {
            JOptionPane.showMessageDialog(parent,
                "GNOME Terminal (gnome-terminal) was not found on your PATH.\n"
                    + "Install it, or launch kiro-cli manually in:\n" + directory,
                "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Fire-and-forget, never waitFor(): Ubuntu's gnome-terminal wrapper script blocks until
        // the terminal window itself closes (confirmed by reading /usr/bin/gnome-terminal), so
        // waiting here would hang for the whole session's lifetime, not just the launch.
        startFireAndForget(parent, buildLinuxCommand(directory));
    }

    private static void launchWindows(Component parent, String directory, boolean skipProfile) {
        if (findOnPath(System.getenv("PATH"), PWSH_BINARY, File::canExecute) == null) {
            JOptionPane.showMessageDialog(parent,
                "PowerShell 7 (pwsh) was not found on your PATH.\n"
                    + "This requires PowerShell 7, not the older Windows PowerShell 5.1.\n"
                    + "Install it, or launch kiro-cli manually in:\n" + directory,
                "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        startFireAndForget(parent, buildWindowsCommand(directory, skipProfile));
    }

    private static void startFireAndForget(Component parent, List<String> command) {
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            logger.warn("Failed to launch terminal session: {}", command, e);
            JOptionPane.showMessageDialog(parent,
                "Failed to launch: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void launchMac(Component parent, String directory) {
        List<String> command = buildMacCommand(directory);
        try {
            Process process = new ProcessBuilder(command).start();
            // osascript is a short-lived Apple-Event dispatcher (not the terminal session itself),
            // so a short bounded wait here is safe -- unlike gnome-terminal/pwsh.exe, it's not
            // going to block for the session's lifetime.
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (finished && process.exitValue() != 0) {
                String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                logger.warn("osascript exited {} launching terminal: {}", process.exitValue(), stderr);
                if (stderr.contains("-1743")) {
                    // -1743: user denied (or has never granted) the one-time Automation
                    // permission prompt to control Terminal.app via Apple events. macOS
                    // never re-prompts after a denial, so the only way back is System
                    // Settings -- see #37.
                    JOptionPane.showMessageDialog(parent,
                        "Kiro Control Panel isn't allowed to control Terminal.\n\n"
                            + "Open System Settings → Privacy & Security → Automation, "
                            + "find Kiro Control Panel, and enable Terminal.",
                        "Permission Needed", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(parent,
                        "Failed to launch Terminal: " + (stderr.isEmpty() ? "exit code " + process.exitValue() : stderr),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            // Otherwise: launched, or still pending (e.g. a one-time Automation permission
            // prompt) -- not treated as a failure either way.
        } catch (IOException e) {
            logger.warn("Failed to launch terminal session: {}", command, e);
            JOptionPane.showMessageDialog(parent,
                "Failed to launch: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

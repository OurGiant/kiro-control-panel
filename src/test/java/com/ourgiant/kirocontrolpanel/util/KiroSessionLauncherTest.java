package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiroSessionLauncherTest {

    @Test
    void detectsMacOsFromVariousOsNameStrings() {
        assertEquals(KiroSessionLauncher.Platform.MACOS, KiroSessionLauncher.detect("Mac OS X"));
        assertEquals(KiroSessionLauncher.Platform.MACOS, KiroSessionLauncher.detect("macOS"));
        assertEquals(KiroSessionLauncher.Platform.MACOS, KiroSessionLauncher.detect("Darwin"));
    }

    @Test
    void detectsWindows() {
        assertEquals(KiroSessionLauncher.Platform.WINDOWS, KiroSessionLauncher.detect("Windows 11"));
    }

    @Test
    void detectsLinux() {
        assertEquals(KiroSessionLauncher.Platform.LINUX, KiroSessionLauncher.detect("Linux"));
    }

    @Test
    void unsupportedForUnknownOrNullOsName() {
        assertEquals(KiroSessionLauncher.Platform.UNSUPPORTED, KiroSessionLauncher.detect("FreeBSD"));
        assertEquals(KiroSessionLauncher.Platform.UNSUPPORTED, KiroSessionLauncher.detect(null));
    }

    @Test
    void linuxCommandEmbedsDirectoryVerbatimSinceNoShellIsInvolved() {
        // ProcessBuilder passes argv directly (no shell), so a directory with spaces/quotes
        // needs no escaping at all here -- unlike the macOS/Windows builders.
        String dir = "/home/me/My Project's \"Files\"";

        List<String> command = KiroSessionLauncher.buildLinuxCommand(dir);

        assertEquals(List.of("gnome-terminal", "--working-directory=" + dir,
            "--", "bash", "-c", "kiro-cli; exec bash"), command);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // bash isn't reliably on PATH there; the function itself is POSIX-shell-only anyway
    void shellSingleQuoteEscapingRoundTripsThroughARealShell() throws Exception {
        // Strongest available verification for this specific layer: actually ask bash to
        // interpret the escaped, single-quoted string and confirm it reproduces the original.
        for (String original : List.of(
                "/simple/path",
                "/path with spaces",
                "it's a path",
                "double '' quotes",
                "trailing'")) {
            String escaped = KiroSessionLauncher.escapeShellSingleQuoted(original);
            assertEquals(original, runBashPrintf("'" + escaped + "'"),
                "round trip failed for: " + original);
        }
    }

    private static String runBashPrintf(String singleQuotedLiteral) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", "-c", "printf '%s' " + singleQuotedLiteral).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "bash did not exit in time");
        return output;
    }

    @Test
    void powerShellSingleQuoteEscapingRoundTrips() {
        for (String original : List.of(
                "C:\\simple\\path",
                "C:\\path with spaces",
                "O'Brien's Project",
                "''already doubled''")) {
            String escaped = KiroSessionLauncher.escapePowerShellSingleQuoted(original);
            assertEquals(original, escaped.replace("''", "'"), "round trip failed for: " + original);
        }
    }

    @Test
    void appleScriptDoubleQuoteEscapingRoundTrips() {
        for (String original : List.of(
                "/simple/path",
                "say \"hello\"",
                "back\\slash",
                "both \\ and \" together")) {
            String escaped = KiroSessionLauncher.escapeAppleScriptDoubleQuoted(original);
            // Reverse order from the escape steps: quotes first, then backslashes.
            String unescaped = escaped.replace("\\\"", "\"").replace("\\\\", "\\");
            assertEquals(original, unescaped, "round trip failed for: " + original);
        }
    }

    @Test
    void windowsCommandUsesLiteralPathAndEscapesEmbeddedQuote() {
        List<String> command = KiroSessionLauncher.buildWindowsCommand("C:\\O'Brien's Project", false);

        assertEquals(List.of("cmd.exe", "/c", "start", "Kiro Session", "pwsh.exe", "-NoExit",
            "-Command", "Set-Location -LiteralPath 'C:\\O''Brien''s Project'; kiro-cli"), command);
    }

    @Test
    void windowsCommandAddsNoProfileWhenSkipProfileIsSet() {
        List<String> command = KiroSessionLauncher.buildWindowsCommand("C:\\plain", true);

        assertEquals(List.of("cmd.exe", "/c", "start", "Kiro Session", "pwsh.exe", "-NoExit", "-NoProfile",
            "-Command", "Set-Location -LiteralPath 'C:\\plain'; kiro-cli"), command);
    }

    @ParameterizedTest(name = "rejects unsafe Windows directory character: {0}")
    @ValueSource(strings = {"&", "|", "^", "%", "<", ">", "\""})
    void windowsCommandRejectsDirectoriesContainingCmdMetacharacters(String unsafeChar) {
        // Regression test for #71: cmd.exe /c (needed for #36's console-detach fix) reinterprets
        // its own metacharacters in the reconstructed command line -- a layer the PowerShell
        // single-quote escaping alone doesn't cover. NTFS permits all of these in a folder name.
        String directory = "C:\\evil" + unsafeChar + "calc.exe";

        assertThrows(IllegalArgumentException.class,
            () -> KiroSessionLauncher.buildWindowsCommand(directory, false));
    }

    @Test
    void macCommandActivatesTerminalThenRunsEscapedScript() {
        List<String> command = KiroSessionLauncher.buildMacCommand("/Users/me/plain");

        assertEquals(List.of(
            "osascript",
            "-e", "tell application \"Terminal\" to activate",
            "-e", "tell application \"Terminal\" to do script \"cd '/Users/me/plain' && kiro-cli\""
        ), command);
    }

    @Test
    void findOnPathReturnsNullWhenPathEnvIsBlank() {
        assertNull(KiroSessionLauncher.findOnPath(null, "gnome-terminal", f -> true));
        assertNull(KiroSessionLauncher.findOnPath("", "gnome-terminal", f -> true));
    }

    @Test
    void findOnPathReturnsNullWhenNoDirectoryHasTheBinary() {
        Predicate<File> neverExecutable = f -> false;

        assertNull(KiroSessionLauncher.findOnPath("/usr/bin" + File.pathSeparator + "/bin",
            "gnome-terminal", neverExecutable));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // POSIX permission bits (and hence File.canExecute()'s meaning) don't apply there
    void findOnPathFindsARealExecutableFileOnATempDirPath(@TempDir Path tempDir) throws IOException {
        Path binary = tempDir.resolve("gnome-terminal");
        Files.writeString(binary, "#!/bin/sh\n");
        Files.setPosixFilePermissions(binary, Set.of(
            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));

        String found = KiroSessionLauncher.findOnPath(tempDir.toString(), "gnome-terminal", File::canExecute);

        assertEquals(binary.toAbsolutePath().toString(), found);
    }
}

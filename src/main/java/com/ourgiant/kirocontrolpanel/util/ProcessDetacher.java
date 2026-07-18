package com.ourgiant.kirocontrolpanel.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * On Linux, jpackage's native launcher runs the JVM in the foreground of the
 * invoking shell, blocking the terminal prompt for as long as the tray app
 * stays open. This relaunches the app once as a detached background process
 * so the original invocation can exit immediately and return the prompt.
 * Scoped to the packaged native launcher only (not a plain "java -jar" dev
 * run) so local debugging keeps its normal foreground behavior.
 */
public final class ProcessDetacher {
    public static final String DETACHED_ENV_VAR = "KIRO_CONTROL_PANEL_DETACHED";

    private ProcessDetacher() {
    }

    public static boolean shouldDetach(String osName, String detachedEnvValue, String launcherCommand) {
        if (detachedEnvValue != null || launcherCommand == null) {
            return false;
        }
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("linux")) {
            return false;
        }
        String baseName = launcherCommand.substring(launcherCommand.lastIndexOf('/') + 1);
        return !baseName.equals("java") && !baseName.equals("javaw");
    }

    public static boolean relaunchDetached(String[] args) {
        String command = ProcessHandle.current().info().command().orElse(null);
        if (!shouldDetach(System.getProperty("os.name"), System.getenv(DETACHED_ENV_VAR), command)) {
            return false;
        }

        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(Arrays.asList(args));

        try {
            ProcessBuilder builder = new ProcessBuilder(commandLine)
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")))
                    .redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
            builder.environment().put(DETACHED_ENV_VAR, "1");
            builder.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

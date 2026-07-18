package com.ourgiant.kirocontrolpanel.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Where the app's own logs live — must match src/main/resources/logback.xml's FILE appender exactly. */
public final class LogPaths {

    private LogPaths() {
    }

    public static Path logsDir() {
        return Paths.get(System.getProperty("user.home"), ".kiro-control-panel", "logs");
    }

    public static Path currentLogFile() {
        return logsDir().resolve("app.log");
    }
}

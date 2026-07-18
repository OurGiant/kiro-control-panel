package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogPathsTest {

    @Test
    void matchesLogbackXmlConfiguredPath() {
        Path expectedDir = Paths.get(System.getProperty("user.home"), ".kiro-control-panel", "logs");
        assertEquals(expectedDir, LogPaths.logsDir());
        assertEquals(expectedDir.resolve("app.log"), LogPaths.currentLogFile());
    }
}

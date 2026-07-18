package com.ourgiant.kirocontrolpanel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDetacherTest {

    @Test
    void detachesForPackagedLinuxLauncher() {
        assertTrue(ProcessDetacher.shouldDetach("Linux", null, "/opt/kiro-control-panel/bin/kiro-control-panel"));
    }

    @Test
    void doesNotDetachWhenAlreadyDetached() {
        assertFalse(ProcessDetacher.shouldDetach("Linux", "1", "/opt/kiro-control-panel/bin/kiro-control-panel"));
    }

    @Test
    void doesNotDetachOnWindows() {
        assertFalse(ProcessDetacher.shouldDetach("Windows 11", null, "C:\\Program Files\\Kiro Control Panel\\kiro-control-panel.exe"));
    }

    @Test
    void doesNotDetachOnMac() {
        assertFalse(ProcessDetacher.shouldDetach("Mac OS X", null, "/Applications/Kiro Control Panel.app/Contents/MacOS/kiro-control-panel"));
    }

    @Test
    void doesNotDetachForPlainJavaDevRun() {
        assertFalse(ProcessDetacher.shouldDetach("Linux", null, "/usr/lib/jvm/java-24-amazon-corretto/bin/java"));
    }

    @Test
    void doesNotDetachForPlainJavawDevRun() {
        assertFalse(ProcessDetacher.shouldDetach("Linux", null, "/usr/lib/jvm/java-24-amazon-corretto/bin/javaw"));
    }

    @Test
    void doesNotDetachWhenCommandUnknown() {
        assertFalse(ProcessDetacher.shouldDetach("Linux", null, null));
    }

    @Test
    void doesNotDetachWhenOsNameUnknown() {
        assertFalse(ProcessDetacher.shouldDetach(null, null, "/opt/kiro-control-panel/bin/kiro-control-panel"));
    }
}

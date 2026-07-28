package com.ourgiant.kirocontrolpanel.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens a file's containing folder in the OS file manager, for "reveal" actions across panels. */
public final class DesktopUtils {
    private static final Logger logger = LoggerFactory.getLogger(DesktopUtils.class);

    private DesktopUtils() {
    }

    /** Opens {@code path} in the OS file manager if it's a directory, or its containing folder if it's a file. */
    public static void revealInFileManager(Component parent, Path path) {
        if (path == null) {
            return;
        }
        Path toOpen = Files.isDirectory(path) ? path : path.getParent();
        if (toOpen == null || !Files.exists(toOpen)) {
            JOptionPane.showMessageDialog(parent,
                "That location doesn't exist yet:\n" + path, "Not Found", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(parent,
                "Opening a file manager isn't supported on this platform.\nLocation: " + toOpen,
                "Not Supported", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(toOpen.toFile());
        } catch (IOException e) {
            logger.warn("Failed to open {} in file manager", toOpen, e);
            JOptionPane.showMessageDialog(parent,
                "Failed to open: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

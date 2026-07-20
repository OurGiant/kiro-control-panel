package com.ourgiant.kirocontrolpanel.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Loads the app icon artwork and scales it to the requested size. Source is
 * a 1024x1024 PNG shipped as a classpath resource, so every caller (window
 * titlebar, system tray, About dialog) renders the same ghost mascot instead
 * of each platform picking up whatever icon its native chrome falls back to.
 */
public final class IconFactory {

    private static final String RESOURCE_PATH = "/app-icon.png";

    private static volatile BufferedImage source;

    private IconFactory() {
    }

    public static BufferedImage createAppIcon(int size) {
        BufferedImage src = loadSource();
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static BufferedImage loadSource() {
        BufferedImage loaded = source;
        if (loaded == null) {
            synchronized (IconFactory.class) {
                loaded = source;
                if (loaded == null) {
                    try (InputStream in = Objects.requireNonNull(
                            IconFactory.class.getResourceAsStream(RESOURCE_PATH),
                            "Missing classpath resource: " + RESOURCE_PATH)) {
                        loaded = ImageIO.read(in);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to load app icon resource " + RESOURCE_PATH, e);
                    }
                    source = loaded;
                }
            }
        }
        return loaded;
    }
}

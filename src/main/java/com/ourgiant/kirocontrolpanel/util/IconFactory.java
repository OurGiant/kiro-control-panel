package com.ourgiant.kirocontrolpanel.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws the app icon at runtime instead of shipping a raster asset. Good
 * enough for the tray/taskbar through M0-M5; a designed .icns/.ico set is
 * planned for M6 native packaging (see SPEC.md).
 */
public final class IconFactory {

    private static final Color BACKGROUND = new Color(0x2F, 0x81, 0xF7);
    private static final Color FOREGROUND = Color.WHITE;

    private IconFactory() {
    }

    public static BufferedImage createAppIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = size / 4;
            g.setColor(BACKGROUND);
            g.fillRoundRect(0, 0, size, size, arc, arc);

            g.setColor(FOREGROUND);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) (size * 0.6));
            g.setFont(font);
            String text = "K";
            var metrics = g.getFontMetrics();
            int textX = (size - metrics.stringWidth(text)) / 2;
            int textY = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, textX, textY);
        } finally {
            g.dispose();
        }
        return image;
    }
}

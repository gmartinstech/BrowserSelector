package com.browserselector.util;

import com.browserselector.model.Browser;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Browser icons resolved through the Windows shell. Registry DefaultIcon
 * values point at .exe/.ico resources that {@link ImageIcon} cannot decode
 * (blank gutters), so the shell itself is asked for the icon; a neutral
 * first-letter tile covers everything the shell cannot render. Results are
 * cached per source path.
 */
public final class Icons {

    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final FileSystemView SHELL = FileSystemView.getFileSystemView();

    private Icons() {}

    /** Row height stays predictable: whatever the shell returns is normalized to 22px. */
    private static final int ICON_SIZE = 22;

    public static Icon forBrowser(Browser browser) {
        var path = iconSource(browser);
        var key = path != null ? path.toString() : "tile:" + browser.name();
        return CACHE.computeIfAbsent(key, k -> normalize(load(path, browser.name())));
    }

    /** Scales any shell icon onto a fixed-size canvas so rows never balloon. */
    private static Icon normalize(Icon raw) {
        if (raw.getIconWidth() <= ICON_SIZE && raw.getIconHeight() <= ICON_SIZE) {
            return raw;
        }
        // paintIcon ignores the target size, so rasterize at natural size
        // first, then draw the raster scaled down onto the fixed canvas.
        var natural = new BufferedImage(raw.getIconWidth(), raw.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var ng = natural.createGraphics();
        raw.paintIcon(null, ng, 0, 0);
        ng.dispose();

        var img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(natural, 0, 0, ICON_SIZE, ICON_SIZE, null);
        g.dispose();
        return new ImageIcon(img);
    }

    private static Path iconSource(Browser browser) {
        if (browser.iconPath() != null && Files.exists(browser.iconPath())) {
            return browser.iconPath();
        }
        if (browser.exePath() != null && Files.exists(browser.exePath())) {
            return browser.exePath();
        }
        return null;
    }

    private static Icon load(Path path, String name) {
        if (path != null) {
            var file = path.toFile();
            try {
                // Large 32px shell icon (JDK 17+) — crisp in the list.
                return SHELL.getSystemIcon(file, 32, 96);
            } catch (Throwable ignored) {
                // older runtime: fall through to the 16px variant
            }
            try {
                return SHELL.getSystemIcon(file);
            } catch (Throwable ignored) {
                // fall through to the letter tile
            }
        }
        return letterTile(name);
    }

    /** A neutral first-letter tile so no row ever paints an empty gutter. */
    private static Icon letterTile(String name) {
        var letter = name != null && !name.isBlank()
            ? name.trim().substring(0, 1).toUpperCase()
            : "?";
        var img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(96, 125, 155));
        g.fillRoundRect(0, 0, ICON_SIZE, ICON_SIZE, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        var fm = g.getFontMetrics();
        g.drawString(letter, (ICON_SIZE - fm.stringWidth(letter)) / 2,
            (ICON_SIZE + fm.getAscent() - fm.getDescent()) / 2 - 1);
        g.dispose();
        return new ImageIcon(img);
    }
}

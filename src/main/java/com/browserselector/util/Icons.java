package com.browserselector.util;

import com.browserselector.model.Browser;
import com.browserselector.service.ProfileDetector;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Icons for browser rows. Non-profile browsers get their shell icon; browser
 * PROFILES get a visual identity of their own, in priority order:
 * 1. the profile's real avatar picture (Google Profile Picture.png, written
 *    by Chromium for signed-in profiles), clipped to a circle;
 * 2. a badge in the highlight color Chrome itself uses for that profile's
 *    avatar, with the profile's initial;
 * 3. a hashed-hue badge with the initial, so every profile of a browser is
 *    still visually distinct when no Chrome-side data exists.
 * Results are cached per source.
 */
public final class Icons {

    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Icon> PROFILE_CACHE = new ConcurrentHashMap<>();
    private static final FileSystemView SHELL = FileSystemView.getFileSystemView();
    private static final int ICON_SIZE = 22;

    private Icons() {}

    public static Icon forBrowser(Browser browser) {
        if (browser.isProfile()) {
            return PROFILE_CACHE.computeIfAbsent(browser.id(), k -> profileIcon(browser));
        }
        var path = iconSource(browser);
        var key = path != null ? path.toString() : "tile:" + browser.name();
        return CACHE.computeIfAbsent(key, k -> normalize(load(path, browser.name())));
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
                return SHELL.getSystemIcon(file, 32, 96);
            } catch (Throwable ignored) {
                // older runtime / unsupported size: fall through to the 16px variant
            }
            try {
                return SHELL.getSystemIcon(file);
            } catch (Throwable ignored) {
                // fall through to the letter tile
            }
        }
        return letterTile(name);
    }

    private static Icon profileIcon(Browser browser) {
        ProfileDetector.ProfileVisuals visuals = null;
        try {
            visuals = ProfileDetector.chromiumProfileVisuals(browser);
        } catch (Exception ignored) {
            // Unavailable platform or data — fall through to the hashed badge.
        }
        if (visuals != null && visuals.avatarPath() != null) {
            try {
                var img = ImageIO.read(visuals.avatarPath().toFile());
                if (img != null) {
                    return circular(img);
                }
            } catch (Exception ignored) {
                // Unreadable picture — fall through to the colored badge.
            }
        }
        int rgb;
        if (visuals != null && visuals.highlightColor() != null) {
            rgb = visuals.highlightColor();
        } else {
            float hue = (Math.abs(browser.id().hashCode()) % 360) / 360f;
            rgb = Color.HSBtoRGB(hue, 0.55f, 0.70f);
        }
        return letterBadge(rgb, profileInitial(browser));
    }

    /** The avatar scaled into a circle, the way Chrome draws profile avatars. */
    private static Icon circular(Image source) {
        var img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Float(0, 0, ICON_SIZE, ICON_SIZE));
        g.drawImage(source, 0, 0, ICON_SIZE, ICON_SIZE, null);
        g.dispose();
        return new ImageIcon(img);
    }

    /** A colored disc with the profile's initial, like Chrome's local avatar badges. */
    private static Icon letterBadge(int argb, String letter) {
        var c = new Color(argb, true);
        if (c.getAlpha() == 0) {
            c = new Color(c.getRGB());
        }
        var img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c);
        g.fillOval(0, 0, ICON_SIZE - 1, ICON_SIZE - 1);
        float luminance = 0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue();
        g.setColor(luminance > 140 ? Color.BLACK : Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        var fm = g.getFontMetrics();
        g.drawString(letter, (ICON_SIZE - fm.stringWidth(letter)) / 2,
            (ICON_SIZE + fm.getAscent() - fm.getDescent()) / 2 - 1);
        g.dispose();
        return new ImageIcon(img);
    }

    /** Initial of the profile part of the display name ("Google Chrome (Exadel)" -> "E"). */
    private static String profileInitial(Browser browser) {
        var name = browser.name();
        int open = name.lastIndexOf('(');
        int close = name.lastIndexOf(')');
        if (open >= 0 && close > open) {
            name = name.substring(open + 1, close);
        }
        name = name.trim();
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
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
}

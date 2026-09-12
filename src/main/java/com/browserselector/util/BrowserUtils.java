package com.browserselector.util;

import com.browserselector.model.Browser;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

public final class BrowserUtils {

    private BrowserUtils() {}

    /**
     * Detect the appropriate incognito/private browsing argument based on browser name.
     */
    public static String detectIncognitoArg(String browserName) {
        if (browserName == null) return "--incognito";

        var lower = browserName.toLowerCase();
        if (lower.contains("firefox")) return "-private-window";
        if (lower.contains("opera")) return "--private";
        return "--incognito"; // Chrome, Edge, Brave, etc.
    }

    /**
     * Adds the profile switch to the command line.
     *
     * Chromium browsers expect "--profile-directory=Profile 1" as ONE argument
     * element (never with embedded quotes: the quotes would survive into the
     * switch value Chrome parses and the profile would not resolve). Firefox's
     * "-P <name>" must be split into two separate argv elements.
     * Legacy stored values like --profile-directory="Profile 1" are normalized.
     */
    private static void addProfileArg(java.util.List<String> command, String profileArg) {
        var arg = profileArg.trim();

        int eq = arg.indexOf('=');
        if (eq > 1 && arg.length() > eq + 2
                && arg.charAt(eq + 1) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(0, eq + 1) + arg.substring(eq + 2, arg.length() - 1);
        }

        if (arg.length() > 3 && arg.regionMatches(true, 0, "-P ", 0, 3)) {
            command.add("-P");
            command.add(arg.substring(3).trim());
        } else {
            command.add(arg);
        }
    }

    /**
     * Launch a browser with the given URL.
     *
     * @param browser   The browser to launch
     * @param url       The URL to open
     * @param incognito Whether to open in incognito/private mode
     * @param parent    Parent component for error dialogs (can be null)
     * @return true if launch was successful, false otherwise
     */
    public static boolean launch(Browser browser, String url, boolean incognito, Component parent) {
        try {
            var command = new ArrayList<String>();
            command.add(browser.exePath().toString());

            if (browser.profileArg() != null && !browser.profileArg().isBlank()) {
                addProfileArg(command, browser.profileArg());
            }

            if (incognito && browser.incognitoArg() != null) {
                command.add(browser.incognitoArg());
            }

            command.add(url);

            new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            return true;
        } catch (IOException e) {
            if (parent != null) {
                JOptionPane.showMessageDialog(parent,
                    "Failed to launch browser: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            System.err.println("Failed to launch browser: " + e.getMessage());
            return false;
        }
    }

    /**
     * Launch a browser with the given URL (non-incognito mode).
     */
    public static boolean launch(Browser browser, String url, Component parent) {
        return launch(browser, url, false, parent);
    }

    /**
     * Load the application icon from resources.
     *
     * @return The application icon image, or null if not found
     */
    public static Image loadAppIcon() {
        try {
            var iconUrl = BrowserUtils.class.getResource("/icon.png");
            if (iconUrl != null) {
                return new ImageIcon(iconUrl).getImage();
            }
        } catch (Exception e) {
            // Icon loading failed
        }
        return null;
    }

    /**
     * Set the application icon on a window.
     */
    public static void setAppIcon(Window window) {
        var icon = loadAppIcon();
        if (icon != null) {
            window.setIconImage(icon);
        }
    }
}

package com.browserselector;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.service.BrowserDetector;
import com.browserselector.service.DatabaseService;
import com.browserselector.service.ProfileDetector;
import com.browserselector.ui.SelectorDialog;
import com.browserselector.ui.SettingsFrame;
import com.browserselector.util.BrowserUtils;
import com.browserselector.util.UrlUtils;
import com.browserselector.util.WindowsTheme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.nio.file.Path;

public class Main {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public static void main(String[] args) {
        // Set up look and feel
        setupTheme();

        // Initialize database
        var db = DatabaseService.getInstance();

        // First run: scan for browsers
        if (db.getAllBrowsers().isEmpty()) {
            if (IS_WINDOWS) {
                var detector = new BrowserDetector();
                var browsers = detector.detectBrowsers();
                for (var browser : browsers) {
                    db.saveBrowser(browser);
                }

                // Auto-detect profiles for each detected browser
                var profileDetector = new ProfileDetector();
                for (var browser : browsers) {
                    for (var profile : profileDetector.detectProfiles(browser)) {
                        db.saveBrowser(profile);
                    }
                }
            } else {
                // Demo mode for non-Windows (testing)
                addDemoBrowsers(db);
            }
        }

        SwingUtilities.invokeLater(() -> {
            if (args.length == 0 || args[0].equals("--settings")) {
                // Open settings window
                new SettingsFrame().setVisible(true);
            } else {
                // URL was passed - check for matching rule or show selector
                var url = args[0];
                System.out.println("[BrowserSelector] Received URL: " + url);

                // Validate URL
                if (!UrlUtils.isValidUrl(url)) {
                    url = UrlUtils.normalizeUrl(url);
                    System.out.println("[BrowserSelector] Normalized URL: " + url);
                }

                // Check for existing rule
                var matchingRule = db.findMatchingRule(url);
                if (matchingRule.isPresent()) {
                    var rule = matchingRule.get();
                    System.out.println("[BrowserSelector] Found matching rule: " + rule.pattern() + " -> " + rule.browserId());
                    var browser = db.getBrowser(rule.browserId());

                    if (browser.isPresent()) {
                        System.out.println("[BrowserSelector] Launching: " + browser.get().name());
                        launchBrowser(browser.get(), url);
                        return;
                    }
                }

                // No matching rule - show selector
                System.out.println("[BrowserSelector] No matching rule, showing selector dialog...");
                var dialog = new SelectorDialog(url);
                dialog.setVisible(true);
            }
        });
    }

    private static void setupTheme() {
        try {
            var db = DatabaseService.getInstance();
            var useSystemTheme = db.getToggle(Setting.Toggle.SYSTEM_THEME, true);
            var useDarkTheme = db.getToggle(Setting.Toggle.DARK_THEME, false);

            if (useSystemTheme) {
                // Try to detect system theme (Windows 10/11)
                var isDark = isSystemDarkMode();
                if (isDark) {
                    FlatDarkLaf.setup();
                } else {
                    FlatLightLaf.setup();
                }
            } else if (useDarkTheme) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            // Fallback to light theme
            try {
                FlatLightLaf.setup();
            } catch (Exception ignored) {}
        }
    }

    private static boolean isSystemDarkMode() {
        if (!IS_WINDOWS) return false;
        return WindowsTheme.isSystemDark();
    }

    private static void launchBrowser(Browser browser, String url) {
        BrowserUtils.launch(browser, url, null);
    }

    private static void addDemoBrowsers(DatabaseService db) {
        // Demo browsers for testing on non-Windows platforms
        db.saveBrowser(new Browser("chrome", "Google Chrome",
            Path.of("/usr/bin/google-chrome"), null, null, "--incognito", false, null, true));
        db.saveBrowser(new Browser("firefox", "Mozilla Firefox",
            Path.of("/usr/bin/firefox"), null, null, "-private-window", false, null, true));
        db.saveBrowser(new Browser("brave", "Brave Browser",
            Path.of("/usr/bin/brave-browser"), null, null, "--incognito", false, null, true));
        db.saveBrowser(new Browser("edge", "Microsoft Edge",
            Path.of("/usr/bin/microsoft-edge"), null, null, "--incognito", false, null, true));
    }
}

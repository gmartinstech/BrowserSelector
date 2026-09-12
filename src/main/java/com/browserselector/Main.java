package com.browserselector;

import com.browserselector.model.Browser;
import com.browserselector.model.Setting;
import com.browserselector.service.BrowserDetector;
import com.browserselector.service.DatabaseService;
import com.browserselector.service.ProfileDetector;
import com.browserselector.service.SingleInstanceService;
import com.browserselector.ui.SelectorDialog;
import com.browserselector.ui.SettingsFrame;
import com.browserselector.util.BrowserUtils;
import com.browserselector.util.UrlUtils;
import com.browserselector.util.WindowsTheme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.Window;
import java.nio.file.Path;

public class Main {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public record Payload(String type, String value) {}

    public static void main(String[] args) {
        var payload = toPayload(args);

        // Forward-or-host first: a second process must exit in milliseconds,
        // before any L&F, database, or UI work (spec: startup flow).
        var outcome = SingleInstanceService.acquire(payload.type(), payload.value(),
            (type, value) -> SwingUtilities.invokeLater(() -> dispatch(type, value)));
        if (outcome instanceof SingleInstanceService.Forwarded) {
            return; // delivered to the host; this process is done
        }

        // We are the host process.
        setupTheme();
        var db = DatabaseService.getInstance();

        // First run: scan for browsers (verbatim from the previous main)
        if (db.getAllBrowsers().isEmpty()) {
            if (IS_WINDOWS) {
                var detector = new BrowserDetector();
                var browsers = detector.detectBrowsers();
                for (var browser : browsers) {
                    db.saveBrowser(browser);
                }

                var profileDetector = new ProfileDetector();
                for (var browser : browsers) {
                    for (var profile : profileDetector.detectProfiles(browser)) {
                        db.saveBrowser(profile);
                    }
                }
            } else {
                addDemoBrowsers(db);
            }
        }

        SwingUtilities.invokeLater(() -> dispatch(payload.type(), payload.value()));
        startLingerWatchdog();
    }

    /** Args to wire payload: no args or --settings opens/focuses Settings; anything else is a URL. */
    static Payload toPayload(String[] args) {
        if (args.length == 0 || args[0].equals("--settings")) {
            return new Payload("settings", "");
        }
        var url = args[0];
        if (!UrlUtils.isValidUrl(url)) {
            url = UrlUtils.normalizeUrl(url);
        }
        return new Payload("url", url);
    }

    /** EDT-only. Routes one payload — ours at startup, or forwarded from a second process. */
    private static void dispatch(String type, String value) {
        if (type.equals("settings")) {
            SettingsFrame.focusOrCreate();
            return;
        }
        handleUrl(value);
    }

    private static void handleUrl(String url) {
        System.out.println("[BrowserSelector] Received URL: " + url);

        // Invalid on receipt: drop and log, host keeps serving (spec: error handling)
        if (!UrlUtils.isValidUrl(url)) {
            System.out.println("[BrowserSelector] Dropped invalid URL: " + url);
            return;
        }

        var db = DatabaseService.getInstance();

        // Rule-matched links never enter the pile — decide once, never ask again.
        var matchingRule = db.findMatchingRule(url);
        if (matchingRule.isPresent()) {
            var rule = matchingRule.get();
            System.out.println("[BrowserSelector] Found matching rule: " + rule.pattern() + " -> " + rule.browserId());
            var browser = db.getBrowser(rule.browserId());
            if (browser.isPresent()) {
                System.out.println("[BrowserSelector] Launching: " + browser.get().name());
                BrowserUtils.launch(browser.get(), url, null);
                return;
            }
        }

        System.out.println("[BrowserSelector] No matching rule, showing selector dialog...");
        new SelectorDialog(url).setVisible(true); // Task 4 swaps this to SelectorDialog.enqueue(url)
    }

    /**
     * The host lives while any window is visible, plus a 500 ms linger for
     * near-simultaneous stragglers; a forwarded link arriving during the linger
     * opens a window and resets the clock (spec: "Host lifecycle").
     */
    private static void startLingerWatchdog() {
        var lastVisible = new long[]{System.currentTimeMillis()};
        var timer = new javax.swing.Timer(150, e -> {
            boolean anyVisible = java.util.Arrays.stream(Window.getWindows()).anyMatch(Window::isVisible);
            if (anyVisible) {
                lastVisible[0] = System.currentTimeMillis();
                return;
            }
            if (System.currentTimeMillis() - lastVisible[0] > 500) {
                System.exit(0);
            }
        });
        timer.start();
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

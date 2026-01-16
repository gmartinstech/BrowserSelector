package com.browserselector.service;

import com.browserselector.model.Browser;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BrowserDetector {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

    private static final String[] REGISTRY_PATHS = {
        "SOFTWARE\\Clients\\StartMenuInternet",
        "SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet"
    };

    private static final WinReg.HKEY[] HKEYS = {
        WinReg.HKEY_LOCAL_MACHINE,
        WinReg.HKEY_CURRENT_USER
    };

    // Linux desktop file locations
    private static final String[] LINUX_DESKTOP_PATHS = {
        "/usr/share/applications",
        "/usr/local/share/applications",
        System.getProperty("user.home") + "/.local/share/applications",
        "/var/lib/flatpak/exports/share/applications",
        System.getProperty("user.home") + "/.local/share/flatpak/exports/share/applications",
        "/var/lib/snapd/desktop/applications"
    };

    // Known browsers that might be installed via Microsoft Store or other non-registry methods
    private record KnownBrowser(String id, String name, String[] possiblePaths) {}

    private static final KnownBrowser[] KNOWN_BROWSERS = {
        new KnownBrowser("firefox", "Firefox", new String[] {
            System.getenv("LOCALAPPDATA") + "\\Microsoft\\WindowsApps\\firefox.exe",
            "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
            "C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe"
        }),
        new KnownBrowser("brave", "Brave", new String[] {
            System.getenv("LOCALAPPDATA") + "\\Microsoft\\WindowsApps\\brave.exe",
            System.getenv("LOCALAPPDATA") + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
            "C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe"
        }),
        new KnownBrowser("opera", "Opera", new String[] {
            System.getenv("LOCALAPPDATA") + "\\Microsoft\\WindowsApps\\opera.exe",
            System.getenv("LOCALAPPDATA") + "\\Programs\\Opera\\launcher.exe",
            "C:\\Program Files\\Opera\\launcher.exe"
        }),
        new KnownBrowser("vivaldi", "Vivaldi", new String[] {
            System.getenv("LOCALAPPDATA") + "\\Vivaldi\\Application\\vivaldi.exe",
            "C:\\Program Files\\Vivaldi\\Application\\vivaldi.exe"
        })
    };

    public List<Browser> detectBrowsers() {
        if (IS_LINUX) {
            return detectLinuxBrowsers();
        }
        return detectWindowsBrowsers();
    }

    private List<Browser> detectLinuxBrowsers() {
        var browsers = new ArrayList<Browser>();
        var seenIds = new java.util.HashSet<String>();

        for (var desktopPath : LINUX_DESKTOP_PATHS) {
            var dir = Path.of(desktopPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                continue;
            }

            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".desktop"))
                    .forEach(desktopFile -> {
                        try {
                            var browser = parseDesktopFile(desktopFile);
                            if (browser != null && !seenIds.contains(browser.id())) {
                                browsers.add(browser);
                                seenIds.add(browser.id());
                            }
                        } catch (Exception e) {
                            // Skip this desktop file
                        }
                    });
            } catch (IOException e) {
                // Skip this directory
            }
        }

        return browsers;
    }

    private Browser parseDesktopFile(Path desktopFile) throws IOException {
        var properties = new HashMap<String, String>();
        var inDesktopEntry = false;
        var inAction = false;
        String privateExec = null;

        for (var line : Files.readAllLines(desktopFile)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[")) {
                inDesktopEntry = line.equals("[Desktop Entry]");
                inAction = line.contains("private") || line.contains("incognito");
                continue;
            }

            if (inAction && line.startsWith("Exec=")) {
                privateExec = line.substring(5).trim();
            }

            if (!inDesktopEntry) continue;

            var idx = line.indexOf('=');
            if (idx > 0) {
                var key = line.substring(0, idx).trim();
                var value = line.substring(idx + 1).trim();
                // Only store the first value (non-localized)
                if (!key.contains("[")) {
                    properties.putIfAbsent(key, value);
                }
            }
        }

        // Check if this is a browser (handles HTTP/HTTPS)
        var mimeType = properties.get("MimeType");
        if (mimeType == null || !mimeType.contains("x-scheme-handler/http")) {
            return null;
        }

        // Skip non-application types
        var type = properties.get("Type");
        if (type == null || !type.equals("Application")) {
            return null;
        }

        // Skip hidden or no-display entries
        if ("true".equalsIgnoreCase(properties.get("Hidden")) ||
            "true".equalsIgnoreCase(properties.get("NoDisplay"))) {
            return null;
        }

        var name = properties.get("Name");
        var exec = properties.get("Exec");
        var icon = properties.get("Icon");

        if (name == null || exec == null) {
            return null;
        }

        // Parse executable path from Exec (remove %u, %U, %f, %F, etc.)
        var exePath = parseLinuxExec(exec);
        if (exePath == null) {
            return null;
        }

        // Generate ID from desktop file name
        var fileName = desktopFile.getFileName().toString();
        var id = fileName.replace(".desktop", "").toLowerCase().replaceAll("[^a-z0-9.-]", "-");

        // Detect incognito argument from private action or browser name
        var incognitoArg = detectLinuxIncognitoArg(name, privateExec);

        // Resolve icon path
        Path iconPath = resolveLinuxIcon(icon);

        return new Browser(
            id,
            name,
            exePath,
            iconPath,
            null,
            incognitoArg,
            false,
            null,
            true
        );
    }

    private Path parseLinuxExec(String exec) {
        if (exec == null || exec.isBlank()) {
            return null;
        }

        // Remove field codes (%u, %U, %f, %F, etc.)
        var cleaned = exec.replaceAll("%[a-zA-Z]", "").trim();

        // Handle env vars and command prefixes
        var parts = cleaned.split("\\s+");
        for (var part : parts) {
            if (part.startsWith("/") || part.startsWith("~")) {
                var path = part.replace("~", System.getProperty("user.home"));
                if (Files.exists(Path.of(path))) {
                    return Path.of(path);
                }
            }
            // Try to find in PATH
            var resolved = findInPath(part);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private Path findInPath(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }

        // If already absolute path
        if (command.startsWith("/")) {
            var path = Path.of(command);
            return Files.exists(path) ? path : null;
        }

        // Search in PATH
        var pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (var dir : pathEnv.split(":")) {
                var path = Path.of(dir, command);
                if (Files.exists(path) && Files.isExecutable(path)) {
                    return path;
                }
            }
        }

        // Common locations
        var commonPaths = new String[] {"/usr/bin/", "/usr/local/bin/", "/bin/", "/snap/bin/"};
        for (var prefix : commonPaths) {
            var path = Path.of(prefix + command);
            if (Files.exists(path)) {
                return path;
            }
        }

        return null;
    }

    private Path resolveLinuxIcon(String icon) {
        if (icon == null || icon.isBlank()) {
            return null;
        }

        // If it's an absolute path
        if (icon.startsWith("/")) {
            var path = Path.of(icon);
            return Files.exists(path) ? path : null;
        }

        // Try to find icon in standard locations
        var iconDirs = new String[] {
            "/usr/share/icons/hicolor/256x256/apps",
            "/usr/share/icons/hicolor/128x128/apps",
            "/usr/share/icons/hicolor/64x64/apps",
            "/usr/share/icons/hicolor/48x48/apps",
            "/usr/share/icons/hicolor/scalable/apps",
            "/usr/share/pixmaps"
        };

        var extensions = new String[] {"", ".png", ".svg", ".xpm"};

        for (var dir : iconDirs) {
            for (var ext : extensions) {
                var path = Path.of(dir, icon + ext);
                if (Files.exists(path)) {
                    return path;
                }
            }
        }

        return null;
    }

    private String detectLinuxIncognitoArg(String browserName, String privateExec) {
        // If we found a private/incognito action, extract the argument
        if (privateExec != null) {
            if (privateExec.contains("--incognito")) return "--incognito";
            if (privateExec.contains("--private-window")) return "--private-window";
            if (privateExec.contains("--private")) return "--private";
            if (privateExec.contains("-private-window")) return "-private-window";
        }

        // Fallback based on browser name
        var lower = browserName.toLowerCase();
        if (lower.contains("firefox")) return "-private-window";
        if (lower.contains("opera")) return "--private";
        if (lower.contains("epiphany") || lower.contains("gnome web")) return "--incognito-mode";
        return "--incognito"; // Chrome, Edge, Brave, Chromium, etc.
    }

    private List<Browser> detectWindowsBrowsers() {
        var browsers = new ArrayList<Browser>();
        var seenIds = new java.util.HashSet<String>();

        // First, detect browsers from registry (traditional installation)
        for (var hkey : HKEYS) {
            for (var registryPath : REGISTRY_PATHS) {
                try {
                    if (!Advapi32Util.registryKeyExists(hkey, registryPath)) {
                        continue;
                    }

                    var browserKeys = Advapi32Util.registryGetKeys(hkey, registryPath);
                    for (var browserKey : browserKeys) {
                        try {
                            var browser = readWindowsBrowser(hkey, registryPath + "\\" + browserKey);
                            if (browser != null && !seenIds.contains(browser.id())) {
                                browsers.add(browser);
                                seenIds.add(browser.id());
                            }
                        } catch (Exception e) {
                            // Skip this browser entry
                        }
                    }
                } catch (Exception e) {
                    // Registry path doesn't exist or access denied
                }
            }
        }

        // Then, detect known browsers from fallback paths (Microsoft Store, user installations)
        for (var knownBrowser : KNOWN_BROWSERS) {
            if (seenIds.contains(knownBrowser.id())) {
                continue; // Already found via registry
            }

            for (var pathStr : knownBrowser.possiblePaths()) {
                if (pathStr == null) continue;
                var path = Path.of(pathStr);
                if (Files.exists(path)) {
                    var browser = new Browser(
                        knownBrowser.id(),
                        knownBrowser.name(),
                        path,
                        path, // Use exe as icon source
                        null,
                        detectWindowsIncognitoArg(knownBrowser.name()),
                        false,
                        null,
                        true
                    );
                    browsers.add(browser);
                    seenIds.add(knownBrowser.id());
                    break; // Found this browser, move to next
                }
            }
        }

        return browsers;
    }

    private Browser readWindowsBrowser(WinReg.HKEY hkey, String keyPath) {
        try {
            // Read executable path
            var commandPath = keyPath + "\\shell\\open\\command";
            if (!Advapi32Util.registryKeyExists(hkey, commandPath)) {
                return null;
            }

            var command = Advapi32Util.registryGetStringValue(hkey, commandPath, "");
            var exePath = parseExePath(command);
            if (exePath == null || !Files.exists(exePath)) {
                return null;
            }

            // Read display name
            String name;
            try {
                name = Advapi32Util.registryGetStringValue(hkey, keyPath, "");
                if (name == null || name.isBlank()) {
                    name = exePath.getFileName().toString().replace(".exe", "");
                }
            } catch (Exception e) {
                name = exePath.getFileName().toString().replace(".exe", "");
            }

            // Read icon path
            Path iconPath = null;
            try {
                var iconKeyPath = keyPath + "\\DefaultIcon";
                if (Advapi32Util.registryKeyExists(hkey, iconKeyPath)) {
                    var iconValue = Advapi32Util.registryGetStringValue(hkey, iconKeyPath, "");
                    iconPath = parseIconPath(iconValue);
                }
            } catch (Exception e) {
                // No icon available
            }

            // Generate stable ID from executable path
            var id = generateId(exePath, name);

            return new Browser(
                id,
                name,
                exePath,
                iconPath,
                null,
                detectWindowsIncognitoArg(name),
                false,
                null,
                true
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Path parseExePath(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }

        // Handle quoted paths
        if (command.startsWith("\"")) {
            var endQuote = command.indexOf('"', 1);
            if (endQuote > 1) {
                return Path.of(command.substring(1, endQuote));
            }
        }

        // Handle unquoted paths
        var spaceIdx = command.indexOf(' ');
        var path = spaceIdx > 0 ? command.substring(0, spaceIdx) : command;
        return Path.of(path);
    }

    private Path parseIconPath(String iconValue) {
        if (iconValue == null || iconValue.isBlank()) {
            return null;
        }

        // Icon format is usually "path,index"
        var commaIdx = iconValue.lastIndexOf(',');
        var path = commaIdx > 0 ? iconValue.substring(0, commaIdx) : iconValue;

        // Remove quotes if present
        if (path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }

        var iconPath = Path.of(path);
        return Files.exists(iconPath) ? iconPath : null;
    }

    private String generateId(Path exePath, String name) {
        // Create a stable, readable ID
        var fileName = exePath.getFileName().toString()
            .toLowerCase()
            .replace(".exe", "")
            .replaceAll("[^a-z0-9]", "-");

        // Handle special cases for browser variants
        var parentPath = exePath.getParent();
        if (parentPath != null) {
            var parentName = parentPath.getFileName().toString().toLowerCase();
            if (parentName.contains("canary")) {
                return fileName + "-canary";
            }
            if (parentName.contains("beta")) {
                return fileName + "-beta";
            }
            if (parentName.contains("dev")) {
                return fileName + "-dev";
            }
        }

        return fileName;
    }

    private String detectWindowsIncognitoArg(String browserName) {
        var lower = browserName.toLowerCase();
        if (lower.contains("firefox")) return "-private-window";
        if (lower.contains("opera")) return "--private";
        return "--incognito"; // Chrome, Edge, Brave, etc.
    }
}

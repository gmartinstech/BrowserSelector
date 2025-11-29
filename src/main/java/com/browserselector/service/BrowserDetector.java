package com.browserselector.service;

import com.browserselector.model.Browser;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BrowserDetector {

    private static final String[] REGISTRY_PATHS = {
        "SOFTWARE\\Clients\\StartMenuInternet",
        "SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet"
    };

    private static final WinReg.HKEY[] HKEYS = {
        WinReg.HKEY_LOCAL_MACHINE,
        WinReg.HKEY_CURRENT_USER
    };

    public List<Browser> detectBrowsers() {
        var browsers = new ArrayList<Browser>();
        var seenIds = new java.util.HashSet<String>();

        for (var hkey : HKEYS) {
            for (var registryPath : REGISTRY_PATHS) {
                try {
                    if (!Advapi32Util.registryKeyExists(hkey, registryPath)) {
                        continue;
                    }

                    var browserKeys = Advapi32Util.registryGetKeys(hkey, registryPath);
                    for (var browserKey : browserKeys) {
                        try {
                            var browser = readBrowser(hkey, registryPath + "\\" + browserKey);
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

        return browsers;
    }

    private Browser readBrowser(WinReg.HKEY hkey, String keyPath) {
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
                detectIncognitoArg(name),
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

    private String detectIncognitoArg(String browserName) {
        var lower = browserName.toLowerCase();
        if (lower.contains("firefox")) return "-private-window";
        if (lower.contains("opera")) return "--private";
        return "--incognito"; // Chrome, Edge, Brave, etc.
    }
}

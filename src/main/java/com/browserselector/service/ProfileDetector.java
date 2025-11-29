package com.browserselector.service;

import com.browserselector.model.Browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ProfileDetector {

    public List<Browser> detectProfiles(Browser parentBrowser) {
        var profiles = new ArrayList<Browser>();
        var browserName = parentBrowser.name().toLowerCase();

        if (browserName.contains("chrome") || browserName.contains("chromium")) {
            profiles.addAll(detectChromiumProfiles(parentBrowser, "Google\\Chrome"));
        } else if (browserName.contains("edge")) {
            profiles.addAll(detectChromiumProfiles(parentBrowser, "Microsoft\\Edge"));
        } else if (browserName.contains("brave")) {
            profiles.addAll(detectChromiumProfiles(parentBrowser, "BraveSoftware\\Brave-Browser"));
        } else if (browserName.contains("opera")) {
            profiles.addAll(detectChromiumProfiles(parentBrowser, "Opera Software\\Opera Stable"));
        } else if (browserName.contains("firefox")) {
            profiles.addAll(detectFirefoxProfiles(parentBrowser));
        }

        return profiles;
    }

    private List<Browser> detectChromiumProfiles(Browser parent, String appDataPath) {
        var profiles = new ArrayList<Browser>();
        var localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) return profiles;

        var userDataPath = Path.of(localAppData, appDataPath, "User Data");
        var localStatePath = userDataPath.resolve("Local State");

        if (!Files.exists(localStatePath)) return profiles;

        try {
            var content = Files.readString(localStatePath);
            profiles.addAll(parseChromiumProfiles(parent, content));
        } catch (IOException e) {
            // Can't read profiles
        }

        return profiles;
    }

    private List<Browser> parseChromiumProfiles(Browser parent, String localStateContent) {
        var profiles = new ArrayList<Browser>();

        // Simple JSON parsing for profile_info_cache
        // Format: "Profile N": { "name": "Profile Name", ... }
        var pattern = Pattern.compile("\"(Profile \\d+|Default)\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(localStateContent);

        while (matcher.find()) {
            var profileDir = matcher.group(1);
            var profileName = matcher.group(2);

            // Skip default profile if it's just "Person 1"
            if (profileDir.equals("Default") && profileName.equals("Person 1")) {
                continue;
            }

            var profileId = parent.id() + "-" + profileDir.toLowerCase().replace(" ", "-");
            var displayName = parent.name() + " (" + profileName + ")";
            var profileArg = "--profile-directory=\"" + profileDir + "\"";

            profiles.add(parent.withProfile(profileId, displayName, profileArg));
        }

        return profiles;
    }

    private List<Browser> detectFirefoxProfiles(Browser parent) {
        var profiles = new ArrayList<Browser>();
        var appData = System.getenv("APPDATA");
        if (appData == null) return profiles;

        var profilesIni = Path.of(appData, "Mozilla", "Firefox", "profiles.ini");
        if (!Files.exists(profilesIni)) return profiles;

        try {
            var lines = Files.readAllLines(profilesIni);
            String currentPath = null;
            String currentName = null;
            boolean isRelative = true;

            for (var line : lines) {
                line = line.trim();

                if (line.startsWith("[Profile")) {
                    // Save previous profile if exists
                    if (currentName != null && currentPath != null) {
                        profiles.add(createFirefoxProfile(parent, currentName, currentPath, isRelative));
                    }
                    currentPath = null;
                    currentName = null;
                    isRelative = true;
                } else if (line.startsWith("Name=")) {
                    currentName = line.substring(5);
                } else if (line.startsWith("Path=")) {
                    currentPath = line.substring(5);
                } else if (line.startsWith("IsRelative=")) {
                    isRelative = line.substring(11).equals("1");
                }
            }

            // Don't forget the last profile
            if (currentName != null && currentPath != null) {
                profiles.add(createFirefoxProfile(parent, currentName, currentPath, isRelative));
            }
        } catch (IOException e) {
            // Can't read profiles
        }

        // Remove default profile from list
        profiles.removeIf(p -> p.name().contains("default") || p.name().contains("Default"));

        return profiles;
    }

    private Browser createFirefoxProfile(Browser parent, String name, String path, boolean isRelative) {
        var profileId = parent.id() + "-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
        var displayName = parent.name() + " (" + name + ")";
        var profileArg = "-P \"" + name + "\"";

        return parent.withProfile(profileId, displayName, profileArg);
    }
}

package com.browserselector.service;

import com.browserselector.model.Browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects per-profile browser entries for Chromium-based browsers (via the
 * "Local State" JSON file's profile.info_cache) and Firefox (via profiles.ini).
 *
 * Chromium launch arguments are stored WITHOUT embedded quotes:
 * "--profile-directory=Profile 1" is passed as a single argv element; the
 * process quoting produces exactly one command line token whose value Chrome
 * reads as the profile directory. Wrapping the value in extra quotes breaks
 * profile resolution (the quotes survive into the parsed switch value).
 */
public final class ProfileDetector {

    public List<Browser> detectProfiles(Browser parentBrowser) {
        if (isChromiumFamily(parentBrowser)) {
            return detectChromiumProfiles(parentBrowser);
        }
        if (isFirefox(parentBrowser)) {
            return detectFirefoxProfiles(parentBrowser);
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // Chromium family
    // ------------------------------------------------------------------

    private static boolean isChromiumFamily(Browser browser) {
        var n = (browser.name() + " " + browser.id()).toLowerCase();
        return n.contains("chrome") || n.contains("chromium") || n.contains("edge")
            || n.contains("brave") || n.contains("opera") || n.contains("vivaldi");
    }

    private static boolean isFirefox(Browser browser) {
        var n = (browser.name() + " " + browser.id()).toLowerCase();
        return n.contains("firefox");
    }

    private List<Browser> detectChromiumProfiles(Browser parent) {
        var userDataDir = chromiumUserDataDir(parent);
        if (userDataDir == null) {
            return List.of();
        }

        var localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            return List.of();
        }

        var localStatePath = Path.of(localAppData, userDataDir, "User Data", "Local State");
        if (!Files.exists(localStatePath)) {
            return List.of();
        }

        String content;
        try {
            content = Files.readString(localStatePath);
        } catch (IOException e) {
            return List.of();
        }

        return buildChromiumProfileBrowsers(parent, parseInfoCache(content), parseLastUsed(content));
    }

    /**
     * Resolves the LOCALAPPDATA-relative user data directory for the parent browser,
     * taking variants (Canary/Beta/Dev/Nightly/GX) into account. The variant is
     * inferred first from the executable path, then from the browser name/id.
     */
    static String chromiumUserDataDir(Browser parent) {
        var exe = parent.exePath().toString().toLowerCase().replace('/', '\\');
        var marker = (parent.name() + " " + parent.id()).toLowerCase();

        boolean canary = exe.contains("chrome sxs") || exe.contains("edge sxs")
            || marker.contains("canary");
        boolean nightly = exe.contains("brave-browser-nightly") || marker.contains("nightly");
        boolean beta = exe.contains("chrome beta") || exe.contains("edge beta")
            || exe.contains("brave-browser-beta") || marker.contains("beta");
        boolean dev = exe.contains("chrome dev") || exe.contains("edge dev")
            || exe.contains("brave-browser-dev") || marker.contains("-dev");
        boolean gx = marker.contains("opera gx");

        if (exe.contains("\\google\\chrome") || marker.contains("chrome") || marker.contains("chromium")) {
            if (canary) return "Google\\Chrome SxS";
            if (beta) return "Google\\Chrome Beta";
            if (dev) return "Google\\Chrome Dev";
            return "Google\\Chrome";
        }
        if (exe.contains("\\microsoft\\edge") || marker.contains("edge")) {
            if (canary) return "Microsoft\\Edge SxS";
            if (beta) return "Microsoft\\Edge Beta";
            if (dev) return "Microsoft\\Edge Dev";
            return "Microsoft\\Edge";
        }
        if (exe.contains("\\bravesoftware\\") || marker.contains("brave")) {
            if (nightly) return "BraveSoftware\\Brave-Browser-Nightly";
            if (beta) return "BraveSoftware\\Brave-Browser-Beta";
            if (dev) return "BraveSoftware\\Brave-Browser-Dev";
            return "BraveSoftware\\Brave-Browser";
        }
        if (exe.contains("\\opera software\\") || marker.contains("opera")) {
            if (gx) return "Opera Software\\Opera GX Stable";
            return "Opera Software\\Opera Stable";
        }
        if (exe.contains("\\vivaldi\\") || marker.contains("vivaldi")) {
            return "Vivaldi";
        }
        return null;
    }

    record ProfileEntry(String dir, String name, String gaiaName, String email) {
        boolean isAutoNamed() {
            return name == null || name.matches("(?i)Person \\d+");
        }
    }

    List<Browser> buildChromiumProfileBrowsers(Browser parent, List<ProfileEntry> entries,
                                               String lastUsedDir) {
        // Drop an anonymous, auto-named "Default" profile that is also Chrome's
        // last-used profile: the parent entry already launches exactly that.
        if (lastUsedDir != null) {
            entries.removeIf(e -> "Default".equalsIgnoreCase(e.dir())
                && e.isAutoNamed() && e.dir().equalsIgnoreCase(lastUsedDir.trim()));
        }

        var result = new ArrayList<Browser>();

        // Count how many profiles share each display name, to disambiguate.
        var nameCounts = new LinkedHashMap<String, Integer>();
        for (var e : entries) {
            nameCounts.merge(displayBase(e), 1, Integer::sum);
        }

        for (var e : entries) {
            var base = displayBase(e);
            var label = base;
            if (nameCounts.getOrDefault(base, 0) > 1) {
                label = base + (e.email() != null && !e.email().isBlank()
                    ? " \u00b7 " + e.email()
                    : " (" + e.dir() + ")");
            }

            var profileId = parent.id() + "-"
                + e.dir().toLowerCase().replaceAll("[^a-z0-9]+", "-");
            var displayName = parent.name() + " (" + label + ")";
            var profileArg = "--profile-directory=" + e.dir();

            result.add(parent.withProfile(profileId, displayName, profileArg));
        }
        return result;
    }

    private static String displayBase(ProfileEntry e) {
        if (e.name() != null && !e.name().isBlank()) return e.name().trim();
        if (e.gaiaName() != null && !e.gaiaName().isBlank()) return e.gaiaName().trim();
        return e.dir();
    }

    /**
     * Extracts the profile entries from the "profile" -> "info_cache" object of a
     * Local State JSON document without pulling in a JSON library. Handles nested
     * objects, any profile directory name, and escaped characters inside strings.
     */
    static List<ProfileEntry> parseInfoCache(String json) {
        var entries = new ArrayList<ProfileEntry>();
        int cacheStart = json.indexOf("\"info_cache\"");
        if (cacheStart < 0) {
            return entries;
        }
        int objStart = json.indexOf('{', cacheStart + 12);
        if (objStart < 0) {
            return entries;
        }
        int objEnd = matchingBrace(json, objStart);
        if (objEnd < 0) {
            return entries;
        }
        String obj = json.substring(objStart + 1, objEnd);

        int pos = 0;
        while (true) {
            int keyStart = obj.indexOf('"', pos);
            if (keyStart < 0) break;
            int keyEnd = stringEnd(obj, keyStart);
            if (keyEnd < 0) break;
            String dir = unescape(obj.substring(keyStart + 1, keyEnd - 1));

            int colon = obj.indexOf(':', keyEnd);
            if (colon < 0) break;
            int valueStart = colon + 1;
            while (valueStart < obj.length() && obj.charAt(valueStart) == ' ') valueStart++;
            if (valueStart >= obj.length() || obj.charAt(valueStart) != '{') break;
            int entryEnd = matchingBrace(obj, valueStart);
            if (entryEnd < 0) break;
            String entry = obj.substring(valueStart + 1, entryEnd);

            entries.add(new ProfileEntry(
                dir,
                extractStringField(entry, "name"),
                extractStringField(entry, "gaia_name"),
                extractStringField(entry, "user_name")));

            pos = entryEnd + 1;
        }
        return entries;
    }

    /** Returns the profile directory recorded as last used, or null. */
    static String parseLastUsed(String json) {
        return extractStringField(json, "last_used");
    }

    /** Index of the '}' matching the '{' at {@code open}, honoring JSON strings. */
    private static int matchingBrace(String s, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Given the index of an opening quote, returns the index just past the closing quote. */
    private static int stringEnd(String s, int openQuote) {
        boolean escaped = false;
        for (int i = openQuote + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i + 1;
            }
        }
        return -1;
    }

    /** Extracts the string value of a "field": "value" pair inside {@code json}. */
    private static String extractStringField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + field.length() + 2);
        if (colon < 0) {
            return null;
        }
        int valueStart = json.indexOf('"', colon + 1);
        if (valueStart < 0) {
            return null;
        }
        int valueEnd = stringEnd(json, valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return unescape(json.substring(valueStart + 1, valueEnd - 1));
    }

    /** Unescapes the JSON escape sequences we may encounter in Local State strings. */
    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append('u');
                        }
                    } else {
                        sb.append('u');
                    }
                }
                default -> sb.append(n);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Firefox
    // ------------------------------------------------------------------

    private List<Browser> detectFirefoxProfiles(Browser parent) {
        var profiles = new ArrayList<Browser>();
        var appData = System.getenv("APPDATA");
        if (appData == null) {
            return profiles;
        }

        var profilesIni = Path.of(appData, "Mozilla", "Firefox", "profiles.ini");
        if (!Files.exists(profilesIni)) {
            return profiles;
        }

        try {
            var lines = Files.readAllLines(profilesIni);
            String currentPath = null;
            String currentName = null;
            boolean isRelative = true;

            for (var line : lines) {
                line = line.trim();

                if (line.startsWith("[Profile") || line.startsWith("[Install")) {
                    if (currentName != null && currentPath != null) {
                        addFirefoxProfile(profiles, parent, currentName, currentPath);
                    }
                    currentPath = null;
                    currentName = null;
                } else if (line.startsWith("Name=")) {
                    currentName = line.substring(5).trim();
                } else if (line.startsWith("Path=")) {
                    currentPath = line.substring(5).trim();
                }
            }
            if (currentName != null && currentPath != null) {
                addFirefoxProfile(profiles, parent, currentName, currentPath);
            }
        } catch (IOException e) {
            // Can't read profiles
        }
        return profiles;
    }

    private void addFirefoxProfile(List<Browser> profiles, Browser parent, String name, String path) {
        // Skip Firefox's anonymous default profiles; the parent entry covers
        // launching with the default profile. Keep user-named ones.
        if (name.matches("(?i)default(-release\\d*)?")) {
            return;
        }
        var profileId = parent.id() + "-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        var displayName = parent.name() + " (" + name + ")";
        var profileArg = "-P " + name;
        profiles.add(parent.withProfile(profileId, displayName, profileArg));
    }
}

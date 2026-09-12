package com.browserselector.service;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.nio.file.Path;

public final class RegistryService {

    /** Registry key / ProgId - no spaces: this becomes part of key paths. */
    private static final String APP_KEY = "BrowserSelector";
    /** Display name shown in Windows Settings and RegisteredApplications. */
    private static final String APP_DISPLAY_NAME = "Browser Selector";
    private static final String APP_DESCRIPTION = "Choose which browser to open links with";
    /** Keys written by pre-1.8 builds under the old name; cleaned up on register. */
    private static final String LEGACY_KEY = "BrowserSwitch";

    private static final WinReg.HKEY HKCU = WinReg.HKEY_CURRENT_USER;

    public void registerAsUrlHandler(Path exePath) {
        var exePathStr = exePath.toAbsolutePath().toString();
        var command = "\"" + exePathStr + "\" \"%1\"";

        // Register URL Protocol handler
        var classesPath = "SOFTWARE\\Classes\\" + APP_KEY;
        createKey(classesPath);
        setValue(classesPath, "", "URL:" + APP_DISPLAY_NAME + " Protocol");
        setValue(classesPath, "URL Protocol", "");

        // Set command
        var commandPath = classesPath + "\\shell\\open\\command";
        createKey(commandPath);
        setValue(commandPath, "", command);

        // Set default icon
        var iconPath = classesPath + "\\DefaultIcon";
        createKey(iconPath);
        setValue(iconPath, "", exePathStr + ",0");

        // Register application capabilities
        var capabilitiesPath = "SOFTWARE\\" + APP_KEY + "\\Capabilities";
        createKey(capabilitiesPath);
        setValue(capabilitiesPath, "ApplicationName", APP_DISPLAY_NAME);
        setValue(capabilitiesPath, "ApplicationDescription", APP_DESCRIPTION);

        // Register URL associations
        var urlAssocPath = capabilitiesPath + "\\URLAssociations";
        createKey(urlAssocPath);
        setValue(urlAssocPath, "http", APP_KEY);
        setValue(urlAssocPath, "https", APP_KEY);

        // Register in RegisteredApplications
        var registeredAppsPath = "SOFTWARE\\RegisteredApplications";
        createKey(registeredAppsPath);
        setValue(registeredAppsPath, APP_DISPLAY_NAME, "SOFTWARE\\" + APP_KEY + "\\Capabilities");

        // Also register in StartMenuInternet for visibility
        var startMenuPath = "SOFTWARE\\Clients\\StartMenuInternet\\" + APP_KEY;
        createKey(startMenuPath);
        setValue(startMenuPath, "", APP_DISPLAY_NAME);

        var startMenuCommandPath = startMenuPath + "\\shell\\open\\command";
        createKey(startMenuCommandPath);
        setValue(startMenuCommandPath, "", "\"" + exePathStr + "\" --settings");

        var startMenuIconPath = startMenuPath + "\\DefaultIcon";
        createKey(startMenuIconPath);
        setValue(startMenuIconPath, "", exePathStr + ",0");

        var startMenuCapPath = startMenuPath + "\\Capabilities";
        createKey(startMenuCapPath);
        setValue(startMenuCapPath, "ApplicationName", APP_DISPLAY_NAME);
        setValue(startMenuCapPath, "ApplicationDescription", APP_DESCRIPTION);

        var startMenuUrlPath = startMenuCapPath + "\\URLAssociations";
        createKey(startMenuUrlPath);
        setValue(startMenuUrlPath, "http", APP_KEY);
        setValue(startMenuUrlPath, "https", APP_KEY);

        unregisterLegacy();
    }

    /** Removes registration keys left by pre-1.8 builds under the old name. */
    private void unregisterLegacy() {
        deleteKey("SOFTWARE\\Classes\\" + LEGACY_KEY);
        deleteKey("SOFTWARE\\" + LEGACY_KEY);
        deleteKey("SOFTWARE\\Clients\\StartMenuInternet\\" + LEGACY_KEY);
        try {
            Advapi32Util.registryDeleteValue(HKCU, "SOFTWARE\\RegisteredApplications", LEGACY_KEY);
        } catch (Exception e) {
            // No legacy registration present
        }
    }

    public void unregister() {
        deleteKey("SOFTWARE\\Classes\\" + APP_KEY);
        deleteKey("SOFTWARE\\" + APP_KEY);
        deleteKey("SOFTWARE\\Clients\\StartMenuInternet\\" + APP_KEY);

        try {
            Advapi32Util.registryDeleteValue(HKCU, "SOFTWARE\\RegisteredApplications", APP_DISPLAY_NAME);
        } catch (Exception e) {
            // Key may not exist
        }
    }

    public boolean isRegistered() {
        try {
            return Advapi32Util.registryKeyExists(HKCU, "SOFTWARE\\Classes\\" + APP_KEY) &&
                   Advapi32Util.registryKeyExists(HKCU, "SOFTWARE\\" + APP_KEY + "\\Capabilities");
        } catch (Exception e) {
            return false;
        }
    }

    public void openDefaultAppsSettings() {
        try {
            Runtime.getRuntime().exec("cmd /c start ms-settings:defaultapps");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createKey(String path) {
        try {
            if (!Advapi32Util.registryKeyExists(HKCU, path)) {
                Advapi32Util.registryCreateKey(HKCU, path);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setValue(String path, String name, String value) {
        try {
            Advapi32Util.registrySetStringValue(HKCU, path, name, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteKey(String path) {
        try {
            if (Advapi32Util.registryKeyExists(HKCU, path)) {
                // Delete subkeys first
                var subkeys = Advapi32Util.registryGetKeys(HKCU, path);
                for (var subkey : subkeys) {
                    deleteKey(path + "\\" + subkey);
                }
                Advapi32Util.registryDeleteKey(HKCU, path);
            }
        } catch (Exception e) {
            // Key may not exist
        }
    }
}

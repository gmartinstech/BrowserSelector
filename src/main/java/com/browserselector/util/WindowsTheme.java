package com.browserselector.util;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.awt.Color;

/**
 * Windows personalization state read directly from the registry (fast, no
 * spawned processes): system dark mode and the user's accent color.
 */
public final class WindowsTheme {

    private WindowsTheme() {}

    /** True when Windows personalization is set to dark app mode. */
    public static boolean isSystemDark() {
        try {
            // 0 means dark mode, 1 means light mode
            var value = Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme"
            );
            return value == 0;
        } catch (Exception e) {
            // Registry key may not exist on older Windows versions
            return false;
        }
    }

    /**
     * The user's Windows accent color, or null when unavailable (older
     * Windows or the non-Windows demo mode). The DWM value is stored as
     * 0x00BBGGRR, so the channels are unscrambled here.
     */
    public static Color accentColor() {
        try {
            var value = Advapi32Util.registryGetIntValue(
                WinReg.HKEY_CURRENT_USER,
                "SOFTWARE\\Microsoft\\Windows\\DWM",
                "AccentColor"
            );
            int r = value & 0xFF;
            int g = (value >> 8) & 0xFF;
            int b = (value >> 16) & 0xFF;
            return new Color(r, g, b);
        } catch (Exception e) {
            return null;
        }
    }
}

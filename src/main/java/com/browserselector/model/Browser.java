package com.browserselector.model;

import com.browserselector.util.BrowserUtils;

import java.nio.file.Path;
import java.util.Objects;

public record Browser(
    String id,
    String name,
    Path exePath,
    Path iconPath,
    String profileArg,
    String incognitoArg,
    boolean isProfile,
    String parentBrowserId,
    boolean enabled
) {
    public Browser {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(exePath, "exePath cannot be null");
    }

    public Browser(String id, String name, Path exePath) {
        this(id, name, exePath, null, null, BrowserUtils.detectIncognitoArg(name), false, null, true);
    }

    public Browser withProfile(String profileId, String profileName, String profileArg) {
        return new Browser(
            profileId,
            profileName,
            this.exePath,
            this.iconPath,
            profileArg,
            this.incognitoArg,
            true,
            this.id,
            true
        );
    }

    public Browser withEnabled(boolean enabled) {
        return new Browser(id, name, exePath, iconPath, profileArg, incognitoArg, isProfile, parentBrowserId, enabled);
    }

    public String displayName() {
        return isProfile ? "  " + name : name;
    }
}

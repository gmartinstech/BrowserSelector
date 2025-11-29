package com.browserselector.model;

public sealed interface Setting permits Setting.Toggle, Setting.Text {

    String key();

    record Toggle(String key, boolean value) implements Setting {
        public static final String ADVANCED_MODE = "advanced_mode";
        public static final String SHOW_INCOGNITO = "show_incognito";
        public static final String DARK_THEME = "dark_theme";
        public static final String SYSTEM_THEME = "system_theme";
    }

    record Text(String key, String value) implements Setting {
        public static final String LAST_BROWSER = "last_browser";
    }

    static Toggle toggle(String key, boolean value) {
        return new Toggle(key, value);
    }

    static Text text(String key, String value) {
        return new Text(key, value);
    }
}

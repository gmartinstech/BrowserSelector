package com.browserselector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Arg → payload mapping for the forward-or-host decision. */
class MainTest {

    @Test
    void noArgsMeansSettings() {
        var p = Main.toPayload(new String[0]);
        assertEquals("settings", p.type());
        assertEquals("", p.value());
    }

    @Test
    void settingsFlagMeansSettings() {
        var p = Main.toPayload(new String[]{"--settings"});
        assertEquals("settings", p.type());
    }

    @Test
    void validUrlPassesThrough() {
        var p = Main.toPayload(new String[]{"https://example.org/page?q=1"});
        assertEquals("url", p.type());
        assertEquals("https://example.org/page?q=1", p.value());
    }

    @Test
    void bareDomainIsNormalized() {
        var p = Main.toPayload(new String[]{"example.org"});
        assertEquals("url", p.type());
        assertEquals("https://example.org", p.value());
    }
}

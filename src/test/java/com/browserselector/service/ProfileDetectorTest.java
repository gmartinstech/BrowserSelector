package com.browserselector.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the Local State info_cache parsing used for profile visual identity:
 * highlight colors must be read per profile directory and never leak across.
 */
class ProfileDetectorTest {

    private static final String LOCAL_STATE = """
        {
           "profile": {
              "info_cache": {
                 "Default": {
                    "name": "Person 1",
                    "gaia_name": "",
                    "profile_highlight_color": -2890755
                 },
                 "Profile 1": {
                    "name": "Exadel",
                    "gaia_name": "Gabriel Silva",
                    "profile_highlight_color": -15911582
                 },
                 "Profile 4": {
                    "name": "Gabriel",
                    "profile_highlight_color": -2432263
                 }
              },
              "last_used": "Profile 1"
           }
        }
        """;

    @Test
    void highlightColorIsReadPerProfileDirectory() {
        assertEquals(-2890755, ProfileDetector.extractHighlightColor(LOCAL_STATE, "Default"));
        assertEquals(-15911582, ProfileDetector.extractHighlightColor(LOCAL_STATE, "Profile 1"));
        assertEquals(-2432263, ProfileDetector.extractHighlightColor(LOCAL_STATE, "Profile 4"));
    }

    @Test
    void unknownOrMissingDirectoriesYieldNull() {
        assertNull(ProfileDetector.extractHighlightColor(LOCAL_STATE, "Profile 9"));
        assertNull(ProfileDetector.extractHighlightColor(LOCAL_STATE, "default"));
    }

    @Test
    void missingOrBrokenLocalStateYieldsNull() {
        assertNull(ProfileDetector.extractHighlightColor("{}", "Profile 1"));
        assertNull(ProfileDetector.extractHighlightColor("", "Profile 1"));
        assertNull(ProfileDetector.extractHighlightColor("{ broken", "Profile 1"));
    }

    @Test
    void lastUsedIsParsed() {
        assertEquals("Profile 1", ProfileDetector.parseLastUsed(LOCAL_STATE));
    }
}

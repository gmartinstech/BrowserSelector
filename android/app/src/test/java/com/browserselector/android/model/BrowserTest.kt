package com.browserselector.android.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Browser model.
 */
class BrowserTest {

    // ==================== withEnabled Tests ====================

    @Test
    fun `withEnabled returns copy with updated state`() {
        val browser = Browser(
            packageName = "com.android.chrome",
            name = "Chrome",
            activityName = "com.google.android.apps.chrome.Main",
            enabled = true,
            isDefault = false
        )

        val disabledBrowser = browser.withEnabled(false)

        // Original should be unchanged
        assertTrue(browser.enabled)

        // New copy should have updated value
        assertFalse(disabledBrowser.enabled)

        // Other properties should remain the same
        assertEquals(browser.packageName, disabledBrowser.packageName)
        assertEquals(browser.name, disabledBrowser.name)
        assertEquals(browser.activityName, disabledBrowser.activityName)
        assertEquals(browser.isDefault, disabledBrowser.isDefault)
    }

    @Test
    fun `withEnabled toggle works correctly`() {
        val browser = Browser(
            packageName = "com.android.chrome",
            name = "Chrome",
            activityName = "com.google.android.apps.chrome.Main",
            enabled = false
        )

        val enabledBrowser = browser.withEnabled(true)
        assertTrue(enabledBrowser.enabled)
        assertFalse(browser.enabled)
    }

    // ==================== withDefault Tests ====================

    @Test
    fun `withDefault returns copy with updated state`() {
        val browser = Browser(
            packageName = "org.mozilla.firefox",
            name = "Firefox",
            activityName = "org.mozilla.firefox.App",
            enabled = true,
            isDefault = false
        )

        val defaultBrowser = browser.withDefault(true)

        // Original should be unchanged
        assertFalse(browser.isDefault)

        // New copy should have updated value
        assertTrue(defaultBrowser.isDefault)

        // Other properties should remain the same
        assertEquals(browser.packageName, defaultBrowser.packageName)
        assertEquals(browser.name, defaultBrowser.name)
        assertEquals(browser.activityName, defaultBrowser.activityName)
        assertEquals(browser.enabled, defaultBrowser.enabled)
    }

    @Test
    fun `withDefault can unset default`() {
        val browser = Browser(
            packageName = "org.mozilla.firefox",
            name = "Firefox",
            activityName = "org.mozilla.firefox.App",
            isDefault = true
        )

        val nonDefaultBrowser = browser.withDefault(false)
        assertFalse(nonDefaultBrowser.isDefault)
        assertTrue(browser.isDefault)
    }

    // ==================== getIncognitoExtra Tests ====================

    @Test
    fun `getIncognitoExtra for Chrome returns correct extra`() {
        val chromeExtra = Browser.getIncognitoExtra("com.android.chrome")
        assertNotNull(chromeExtra)
        assertEquals("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", chromeExtra!!.first)
        assertTrue(chromeExtra.second)
    }

    @Test
    fun `getIncognitoExtra for Chrome variants returns correct extra`() {
        listOf("com.chrome.beta", "com.chrome.dev", "com.chrome.canary").forEach { packageName ->
            val extra = Browser.getIncognitoExtra(packageName)
            assertNotNull("Should return extra for $packageName", extra)
            assertEquals("com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB", extra!!.first)
            assertTrue(extra.second)
        }
    }

    @Test
    fun `getIncognitoExtra for Firefox returns correct extra`() {
        val firefoxExtra = Browser.getIncognitoExtra("org.mozilla.firefox")
        assertNotNull(firefoxExtra)
        assertEquals("private_browsing_mode", firefoxExtra!!.first)
        assertTrue(firefoxExtra.second)
    }

    @Test
    fun `getIncognitoExtra for Firefox variants returns correct extra`() {
        listOf("org.mozilla.firefox_beta", "org.mozilla.fenix").forEach { packageName ->
            val extra = Browser.getIncognitoExtra(packageName)
            assertNotNull("Should return extra for $packageName", extra)
            assertEquals("private_browsing_mode", extra!!.first)
            assertTrue(extra.second)
        }
    }

    @Test
    fun `getIncognitoExtra for Brave returns correct extra`() {
        val braveExtra = Browser.getIncognitoExtra("com.brave.browser")
        assertNotNull(braveExtra)
        assertEquals("com.brave.open_private_tab", braveExtra!!.first)
        assertTrue(braveExtra.second)
    }

    @Test
    fun `getIncognitoExtra for Edge returns correct extra`() {
        val edgeExtra = Browser.getIncognitoExtra("com.microsoft.emmx")
        assertNotNull(edgeExtra)
        assertEquals("com.microsoft.emmx.EXTRA_OPEN_IN_PRIVATE", edgeExtra!!.first)
        assertTrue(edgeExtra.second)
    }

    @Test
    fun `getIncognitoExtra for Opera returns correct extra`() {
        val operaExtra = Browser.getIncognitoExtra("com.opera.browser")
        assertNotNull(operaExtra)
        assertEquals("private", operaExtra!!.first)
        assertTrue(operaExtra.second)
    }

    @Test
    fun `getIncognitoExtra returns null for unknown browser`() {
        val unknownExtra = Browser.getIncognitoExtra("com.unknown.browser")
        assertNull(unknownExtra)
    }

    @Test
    fun `getIncognitoExtra returns null for Samsung browser`() {
        val samsungExtra = Browser.getIncognitoExtra("com.sec.android.app.sbrowser")
        assertNull(samsungExtra)
    }

    // ==================== KNOWN_BROWSERS Tests ====================

    @Test
    fun `KNOWN_BROWSERS contains major browsers`() {
        // Chrome family
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.android.chrome"))
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.chrome.beta"))
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.chrome.dev"))

        // Firefox family
        assertTrue(Browser.KNOWN_BROWSERS.contains("org.mozilla.firefox"))
        assertTrue(Browser.KNOWN_BROWSERS.contains("org.mozilla.fenix"))
        assertTrue(Browser.KNOWN_BROWSERS.contains("org.mozilla.focus"))

        // Brave
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.brave.browser"))

        // Edge
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.microsoft.emmx"))

        // Opera
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.opera.browser"))
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.opera.mini.native"))

        // Samsung Internet
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.sec.android.app.sbrowser"))

        // DuckDuckGo
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.duckduckgo.mobile.android"))

        // Vivaldi
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.vivaldi.browser"))

        // Kiwi Browser
        assertTrue(Browser.KNOWN_BROWSERS.contains("com.kiwibrowser.browser"))
    }

    @Test
    fun `KNOWN_BROWSERS is not empty`() {
        assertTrue(Browser.KNOWN_BROWSERS.isNotEmpty())
    }

    @Test
    fun `KNOWN_BROWSERS contains expected number of browsers`() {
        // Verify we have a reasonable number of known browsers
        assertTrue(Browser.KNOWN_BROWSERS.size >= 15)
    }

    // ==================== Default Values Tests ====================

    @Test
    fun `Browser has correct default values`() {
        val browser = Browser(
            packageName = "com.test.browser",
            name = "Test Browser",
            activityName = "com.test.browser.MainActivity"
        )

        assertTrue(browser.enabled)
        assertFalse(browser.isDefault)
        assertNull(browser.icon)
    }
}

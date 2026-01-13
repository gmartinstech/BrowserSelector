package com.browserselector.android.model

import android.graphics.drawable.Drawable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Represents an installed browser on the device.
 */
@Entity(tableName = "browsers")
data class Browser(
    @PrimaryKey
    val packageName: String,

    val name: String,

    val activityName: String,

    val enabled: Boolean = true,

    val isDefault: Boolean = false
) {
    @Ignore
    var icon: Drawable? = null

    fun withEnabled(enabled: Boolean): Browser = copy(enabled = enabled)

    fun withDefault(isDefault: Boolean): Browser = copy(isDefault = isDefault)

    companion object {
        /**
         * Known browser package names for identification.
         */
        val KNOWN_BROWSERS = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.brave.browser_nightly",
            "com.vivaldi.browser",
            "com.microsoft.emmx",
            "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "mark.via.gp",
            "com.cloudmosa.puffin.ex",
            "org.chromium.webview_shell",
            "com.UCMobile.intl",
            "com.opera.browser.beta",
            "org.bromite.bromite",
            "org.ungoogled.nickel",
            "com.mycompany.app.soulbrowser"
        )

        /**
         * Incognito/private mode intent extras for known browsers.
         */
        fun getIncognitoExtra(packageName: String): Pair<String, Boolean>? {
            return when (packageName) {
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary" -> "com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB" to true

                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "org.mozilla.fenix" -> "private_browsing_mode" to true

                "com.brave.browser",
                "com.brave.browser_beta",
                "com.brave.browser_nightly" -> "com.brave.open_private_tab" to true

                "com.microsoft.emmx" -> "com.microsoft.emmx.EXTRA_OPEN_IN_PRIVATE" to true

                "com.opera.browser",
                "com.opera.browser.beta" -> "private" to true

                else -> null
            }
        }
    }
}

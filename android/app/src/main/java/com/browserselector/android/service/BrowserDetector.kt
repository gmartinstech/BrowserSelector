package com.browserselector.android.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import com.browserselector.android.model.Browser

/**
 * Service to detect installed browsers on the device.
 * Uses PackageManager to query for apps that can handle HTTP/HTTPS URLs.
 */
class BrowserDetector(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Detects all installed browsers by querying for apps that can handle HTTP URLs.
     */
    fun detectBrowsers(): List<Browser> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val resolveInfoList = queryIntentActivities(intent)
        val browsers = mutableListOf<Browser>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName

            // Skip our own app and duplicates
            if (packageName == context.packageName || seenPackages.contains(packageName)) {
                continue
            }

            seenPackages.add(packageName)

            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                resolveInfo.loadLabel(packageManager).toString()
            }

            val browser = Browser(
                packageName = packageName,
                name = appName,
                activityName = resolveInfo.activityInfo.name,
                enabled = true,
                isDefault = false
            )

            browsers.add(browser)
        }

        // Sort known browsers first, then alphabetically
        return browsers.sortedWith(compareBy(
            { if (Browser.KNOWN_BROWSERS.contains(it.packageName)) 0 else 1 },
            { it.name.lowercase() }
        ))
    }

    /**
     * Loads icons for the given list of browsers.
     */
    fun loadIcons(browsers: List<Browser>): List<Browser> {
        return browsers.map { browser ->
            try {
                browser.icon = packageManager.getApplicationIcon(browser.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                browser.icon = null
            }
            browser
        }
    }

    /**
     * Gets the icon for a single browser.
     */
    fun loadIcon(browser: Browser): Browser {
        try {
            browser.icon = packageManager.getApplicationIcon(browser.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            browser.icon = null
        }
        return browser
    }

    /**
     * Checks if a specific package is installed.
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Gets the default browser package name, if one is set.
     */
    fun getSystemDefaultBrowser(): String? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return resolveInfo?.activityInfo?.packageName?.takeIf {
            it != "android" && it != context.packageName
        }
    }

    private fun queryIntentActivities(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
    }
}

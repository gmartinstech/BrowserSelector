package com.browserselector.android.data

import android.content.Context
import com.browserselector.android.model.Browser
import com.browserselector.android.model.UrlRule
import com.browserselector.android.service.BrowserDetector
import com.browserselector.android.util.PatternMatcher
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing a clean API for data access.
 * Manages browsers and URL rules.
 */
class BrowserRepository(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val browserDao = database.browserDao()
    private val urlRuleDao = database.urlRuleDao()
    private val browserDetector = BrowserDetector(context)

    // Browser operations

    val allBrowsers: Flow<List<Browser>> = browserDao.getAllBrowsers()
    val enabledBrowsers: Flow<List<Browser>> = browserDao.getEnabledBrowsers()

    suspend fun getAllBrowsersList(): List<Browser> = browserDao.getAllBrowsersList()

    suspend fun getEnabledBrowsersList(): List<Browser> = browserDao.getEnabledBrowsersList()

    suspend fun getBrowser(packageName: String): Browser? = browserDao.getBrowser(packageName)

    suspend fun getDefaultBrowser(): Browser? = browserDao.getDefaultBrowser()

    suspend fun insertBrowser(browser: Browser) = browserDao.insertBrowser(browser)

    suspend fun updateBrowser(browser: Browser) = browserDao.updateBrowser(browser)

    suspend fun deleteBrowser(browser: Browser) = browserDao.deleteBrowser(browser)

    suspend fun setBrowserEnabled(packageName: String, enabled: Boolean) =
        browserDao.setEnabled(packageName, enabled)

    suspend fun setDefaultBrowser(packageName: String) {
        browserDao.clearDefaultBrowser()
        browserDao.setDefaultBrowser(packageName)
    }

    suspend fun clearDefaultBrowser() = browserDao.clearDefaultBrowser()

    /**
     * Scans for installed browsers and updates the database.
     * Preserves enabled/default state for existing browsers.
     */
    suspend fun refreshBrowsers() {
        val existingBrowsers = browserDao.getAllBrowsersList().associateBy { it.packageName }
        val detectedBrowsers = browserDetector.detectBrowsers()

        val updatedBrowsers = detectedBrowsers.map { detected ->
            val existing = existingBrowsers[detected.packageName]
            if (existing != null) {
                detected.copy(enabled = existing.enabled, isDefault = existing.isDefault)
            } else {
                detected
            }
        }

        browserDao.insertBrowsers(updatedBrowsers)
    }

    /**
     * Loads icons for browsers using PackageManager.
     */
    fun loadBrowserIcons(browsers: List<Browser>): List<Browser> {
        return browserDetector.loadIcons(browsers)
    }

    // URL Rule operations

    val allRules: Flow<List<UrlRule>> = urlRuleDao.getAllRules()

    suspend fun getAllRulesList(): List<UrlRule> = urlRuleDao.getAllRulesList()

    suspend fun getRule(id: Int): UrlRule? = urlRuleDao.getRule(id)

    suspend fun insertRule(rule: UrlRule): Long = urlRuleDao.insertRule(rule)

    suspend fun updateRule(rule: UrlRule) = urlRuleDao.updateRule(rule)

    suspend fun deleteRule(rule: UrlRule) = urlRuleDao.deleteRule(rule)

    suspend fun deleteRuleById(id: Int) = urlRuleDao.deleteRuleById(id)

    /**
     * Finds the first matching rule for the given URL.
     * Rules are checked in priority order (highest first).
     */
    suspend fun findMatchingRule(url: String): UrlRule? {
        val rules = urlRuleDao.getAllRulesList()
        return rules.firstOrNull { rule ->
            PatternMatcher.matches(rule.pattern, url)
        }
    }

    /**
     * Creates a new rule with the next available priority.
     */
    suspend fun createRule(pattern: String, browserPackage: String): UrlRule {
        val maxPriority = urlRuleDao.getMaxPriority() ?: 0
        val rule = UrlRule(
            pattern = pattern,
            browserPackage = browserPackage,
            priority = maxPriority + 1
        )
        val id = urlRuleDao.insertRule(rule)
        return rule.copy(id = id.toInt())
    }

    // Initialization

    suspend fun initializeIfNeeded() {
        if (browserDao.getBrowserCount() == 0) {
            refreshBrowsers()
        }
    }
}

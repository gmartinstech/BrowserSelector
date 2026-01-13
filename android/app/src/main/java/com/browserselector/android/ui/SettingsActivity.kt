package com.browserselector.android.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.browserselector.android.BrowserSelectorApp
import com.browserselector.android.R
import com.browserselector.android.databinding.ActivitySettingsBinding
import com.browserselector.android.model.Browser
import com.browserselector.android.model.UrlRule
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings activity with tabs for managing URL rules and browsers.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val repository by lazy { (application as BrowserSelectorApp).repository }

    private lateinit var rulesAdapter: UrlRulesAdapter
    private lateinit var browsersAdapter: SettingsBrowserAdapter

    private var browsers: List<Browser> = emptyList()
    private var rules: List<UrlRule> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        setupTabs()
        setupRulesTab()
        setupBrowsersTab()
        setupSettingsTab()

        loadData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                refreshBrowsers()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> showRulesTab()
                    1 -> showBrowsersTab()
                    2 -> showSettingsTab()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Show rules tab by default
        showRulesTab()
    }

    private fun setupRulesTab() {
        rulesAdapter = UrlRulesAdapter(
            onDeleteClick = { rule -> deleteRule(rule) },
            getBrowserName = { packageName ->
                browsers.find { it.packageName == packageName }?.name ?: packageName
            }
        )

        binding.recyclerViewRules.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = rulesAdapter
        }

        binding.fabAddRule.setOnClickListener {
            openAddRuleActivity()
        }
    }

    private fun setupBrowsersTab() {
        browsersAdapter = SettingsBrowserAdapter(
            onEnabledChanged = { browser, enabled ->
                toggleBrowserEnabled(browser, enabled)
            }
        )

        binding.recyclerViewBrowsers.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = browsersAdapter
        }

        binding.buttonRescanBrowsers.setOnClickListener {
            refreshBrowsers()
        }
    }

    private fun setupSettingsTab() {
        binding.buttonOpenDefaultApps.setOnClickListener {
            openDefaultAppsSettings()
        }
    }

    private fun showRulesTab() {
        binding.layoutRules.visibility = View.VISIBLE
        binding.layoutBrowsers.visibility = View.GONE
        binding.layoutSettings.visibility = View.GONE
        binding.fabAddRule.visibility = View.VISIBLE
    }

    private fun showBrowsersTab() {
        binding.layoutRules.visibility = View.GONE
        binding.layoutBrowsers.visibility = View.VISIBLE
        binding.layoutSettings.visibility = View.GONE
        binding.fabAddRule.visibility = View.GONE
    }

    private fun showSettingsTab() {
        binding.layoutRules.visibility = View.GONE
        binding.layoutBrowsers.visibility = View.GONE
        binding.layoutSettings.visibility = View.VISIBLE
        binding.fabAddRule.visibility = View.GONE
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Collect browsers
            launch {
                repository.allBrowsers.collectLatest { browserList ->
                    browsers = withContext(Dispatchers.IO) {
                        repository.loadBrowserIcons(browserList)
                    }
                    browsersAdapter.submitList(browsers)
                    // Update rules adapter with new browser names
                    rulesAdapter.notifyDataSetChanged()
                }
            }

            // Collect rules
            launch {
                repository.allRules.collectLatest { ruleList ->
                    rules = ruleList
                    rulesAdapter.submitList(rules)

                    // Show/hide empty state
                    binding.textViewNoRules.visibility =
                        if (rules.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun deleteRule(rule: UrlRule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_rule)
            .setMessage(getString(R.string.delete_rule_confirm, rule.pattern))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.deleteRule(rule)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toggleBrowserEnabled(browser: Browser, enabled: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            repository.setBrowserEnabled(browser.packageName, enabled)
        }
    }

    private fun refreshBrowsers() {
        binding.progressBarBrowsers.visibility = View.VISIBLE

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.refreshBrowsers()
            }
            binding.progressBarBrowsers.visibility = View.GONE
            Toast.makeText(
                this@SettingsActivity,
                R.string.browsers_refreshed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openAddRuleActivity() {
        val intent = Intent(this, AddRuleActivity::class.java)
        startActivity(intent)
    }

    private fun openDefaultAppsSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_opening_settings, Toast.LENGTH_SHORT).show()
        }
    }
}

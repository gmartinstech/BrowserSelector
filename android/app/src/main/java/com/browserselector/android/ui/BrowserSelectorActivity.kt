package com.browserselector.android.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.browserselector.android.BrowserSelectorApp
import com.browserselector.android.R
import com.browserselector.android.databinding.ActivityBrowserSelectorBinding
import com.browserselector.android.model.Browser
import com.browserselector.android.util.PatternMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity for selecting a browser to open a URL.
 * Displays as a dialog-style activity.
 */
class BrowserSelectorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserSelectorBinding
    private lateinit var adapter: BrowserAdapter

    private val repository by lazy { (application as BrowserSelectorApp).repository }

    private var url: String? = null
    private var browsers: List<Browser> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()

        // Handle the incoming URL
        url = intent?.data?.toString()

        if (url != null) {
            handleUrl(url!!)
        } else {
            // No URL - launched from app drawer, open settings
            openSettings()
        }
    }

    private fun setupRecyclerView() {
        adapter = BrowserAdapter { browser ->
            url?.let { openUrlWithBrowser(it, browser) }
        }

        binding.recyclerViewBrowsers.apply {
            layoutManager = LinearLayoutManager(this@BrowserSelectorActivity)
            adapter = this@BrowserSelectorActivity.adapter
        }
    }

    private fun setupButtons() {
        binding.buttonSettings.setOnClickListener {
            openSettings()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }

        binding.checkboxRemember.setOnCheckedChangeListener { _, isChecked ->
            binding.editTextPattern.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun handleUrl(url: String) {
        binding.textViewUrl.text = PatternMatcher.truncateUrl(url, 60)

        // Set default pattern based on domain
        val domain = PatternMatcher.extractDomain(url)
        if (domain != null) {
            binding.editTextPattern.setText(PatternMatcher.domainToPattern(domain))
        }

        lifecycleScope.launch {
            // Check for matching rule
            val matchingRule = withContext(Dispatchers.IO) {
                repository.findMatchingRule(url)
            }

            if (matchingRule != null) {
                // Found a matching rule - open browser automatically
                val browser = withContext(Dispatchers.IO) {
                    repository.getBrowser(matchingRule.browserPackage)
                }

                if (browser != null && browser.enabled) {
                    openUrlWithBrowser(url, browser)
                    return@launch
                }
            }

            // No matching rule - show browser selection UI
            loadBrowsers()
        }
    }

    private fun loadBrowsers() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            browsers = withContext(Dispatchers.IO) {
                val browserList = repository.getEnabledBrowsersList()
                repository.loadBrowserIcons(browserList)
            }

            binding.progressBar.visibility = View.GONE

            if (browsers.isEmpty()) {
                binding.textViewNoBrowsers.visibility = View.VISIBLE
            } else {
                adapter.submitList(browsers)
            }
        }
    }

    private fun openUrlWithBrowser(url: String, browser: Browser) {
        // Save rule if remember is checked
        if (binding.checkboxRemember.isChecked) {
            val pattern = binding.editTextPattern.text.toString().trim()
            if (pattern.isNotEmpty() && PatternMatcher.isValidPattern(pattern)) {
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.createRule(pattern, browser.packageName)
                }
            }
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                component = ComponentName(browser.packageName, browser.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // Add incognito extra if available (for long-press feature)
            // This could be enhanced with a long-press option in the future

            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                getString(R.string.error_browser_not_found, browser.name),
                Toast.LENGTH_SHORT
            ).show()
            return
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_opening_browser),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        finish()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        if (url == null) {
            finish()
        }
    }
}

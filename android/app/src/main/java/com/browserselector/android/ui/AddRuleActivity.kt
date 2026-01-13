package com.browserselector.android.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.browserselector.android.BrowserSelectorApp
import com.browserselector.android.R
import com.browserselector.android.databinding.ActivityAddRuleBinding
import com.browserselector.android.model.Browser
import com.browserselector.android.util.PatternMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for adding a new URL rule.
 */
class AddRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddRuleBinding

    private val repository by lazy { (application as BrowserSelectorApp).repository }

    private var browsers: List<Browser> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.add_rule)

        setupButtons()
        loadBrowsers()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupButtons() {
        binding.buttonSave.setOnClickListener {
            saveRule()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadBrowsers() {
        lifecycleScope.launch {
            browsers = withContext(Dispatchers.IO) {
                repository.getEnabledBrowsersList()
            }

            val browserNames = browsers.map { it.name }
            val adapter = ArrayAdapter(
                this@AddRuleActivity,
                android.R.layout.simple_spinner_item,
                browserNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerBrowser.adapter = adapter
        }
    }

    private fun saveRule() {
        val pattern = binding.editTextPattern.text.toString().trim()

        // Validate pattern
        if (pattern.isEmpty()) {
            binding.textInputPattern.error = getString(R.string.error_pattern_empty)
            return
        }

        if (!PatternMatcher.isValidPattern(pattern)) {
            binding.textInputPattern.error = getString(R.string.error_pattern_invalid)
            return
        }

        binding.textInputPattern.error = null

        // Get selected browser
        val selectedPosition = binding.spinnerBrowser.selectedItemPosition
        if (selectedPosition < 0 || selectedPosition >= browsers.size) {
            Toast.makeText(this, R.string.error_select_browser, Toast.LENGTH_SHORT).show()
            return
        }

        val selectedBrowser = browsers[selectedPosition]

        // Save rule
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.createRule(pattern, selectedBrowser.packageName)
            }

            Toast.makeText(
                this@AddRuleActivity,
                R.string.rule_saved,
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }
}

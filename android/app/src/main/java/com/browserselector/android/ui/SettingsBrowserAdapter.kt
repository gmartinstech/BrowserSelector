package com.browserselector.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.browserselector.android.databinding.ItemSettingsBrowserBinding
import com.browserselector.android.model.Browser

/**
 * RecyclerView adapter for managing browsers in settings.
 * Allows enabling/disabling browsers.
 */
class SettingsBrowserAdapter(
    private val onEnabledChanged: (Browser, Boolean) -> Unit
) : ListAdapter<Browser, SettingsBrowserAdapter.BrowserViewHolder>(BrowserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder {
        val binding = ItemSettingsBrowserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BrowserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BrowserViewHolder(
        private val binding: ItemSettingsBrowserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(browser: Browser) {
            binding.apply {
                // Set browser icon
                if (browser.icon != null) {
                    imageViewIcon.setImageDrawable(browser.icon)
                } else {
                    imageViewIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }

                // Set browser name and package
                textViewName.text = browser.name
                textViewPackage.text = browser.packageName

                // Set enabled checkbox
                checkboxEnabled.isChecked = browser.enabled
                checkboxEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onEnabledChanged(browser, isChecked)
                }

                // Allow clicking entire row to toggle
                root.setOnClickListener {
                    checkboxEnabled.isChecked = !checkboxEnabled.isChecked
                }
            }
        }
    }

    private class BrowserDiffCallback : DiffUtil.ItemCallback<Browser>() {
        override fun areItemsTheSame(oldItem: Browser, newItem: Browser): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: Browser, newItem: Browser): Boolean {
            return oldItem == newItem
        }
    }
}

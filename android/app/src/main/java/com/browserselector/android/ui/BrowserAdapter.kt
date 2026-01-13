package com.browserselector.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.browserselector.android.databinding.ItemBrowserBinding
import com.browserselector.android.model.Browser

/**
 * RecyclerView adapter for displaying browsers in a list.
 */
class BrowserAdapter(
    private val onBrowserClick: (Browser) -> Unit
) : ListAdapter<Browser, BrowserAdapter.BrowserViewHolder>(BrowserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder {
        val binding = ItemBrowserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BrowserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) {
        val browser = getItem(position)
        holder.bind(browser, position + 1)
    }

    inner class BrowserViewHolder(
        private val binding: ItemBrowserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(browser: Browser, position: Int) {
            binding.apply {
                // Set browser number (1-9 for quick selection)
                textViewNumber.text = if (position <= 9) position.toString() else ""

                // Set browser icon
                if (browser.icon != null) {
                    imageViewIcon.setImageDrawable(browser.icon)
                } else {
                    imageViewIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }

                // Set browser name
                textViewName.text = browser.name

                // Set click listener
                root.setOnClickListener {
                    onBrowserClick(browser)
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

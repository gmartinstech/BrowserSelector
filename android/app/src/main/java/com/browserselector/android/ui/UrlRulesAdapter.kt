package com.browserselector.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.browserselector.android.databinding.ItemUrlRuleBinding
import com.browserselector.android.model.UrlRule

/**
 * RecyclerView adapter for displaying URL rules.
 */
class UrlRulesAdapter(
    private val onDeleteClick: (UrlRule) -> Unit,
    private val getBrowserName: (String) -> String
) : ListAdapter<UrlRule, UrlRulesAdapter.RuleViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemUrlRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RuleViewHolder(
        private val binding: ItemUrlRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: UrlRule) {
            binding.apply {
                textViewPattern.text = rule.pattern
                textViewBrowser.text = getBrowserName(rule.browserPackage)
                textViewPriority.text = rule.priority.toString()

                buttonDelete.setOnClickListener {
                    onDeleteClick(rule)
                }
            }
        }
    }

    private class RuleDiffCallback : DiffUtil.ItemCallback<UrlRule>() {
        override fun areItemsTheSame(oldItem: UrlRule, newItem: UrlRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UrlRule, newItem: UrlRule): Boolean {
            return oldItem == newItem
        }
    }
}

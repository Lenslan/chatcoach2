package com.example.chatcoach.ui.template

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatcoach.data.db.entity.QuickTemplate
import com.example.chatcoach.databinding.ItemTemplateBinding

class TemplateAdapter(
    private val onUse: (QuickTemplate) -> Unit,
    private val onDelete: (QuickTemplate) -> Unit
) : ListAdapter<QuickTemplate, TemplateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(template: QuickTemplate) {
            binding.tvTemplateTitle.text = template.title
            binding.tvTemplateContent.text = template.content
            binding.chipCategory.text = template.category

            binding.btnUse.setOnClickListener { onUse(template) }
            binding.root.setOnLongClickListener {
                onDelete(template)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<QuickTemplate>() {
        override fun areItemsTheSame(oldItem: QuickTemplate, newItem: QuickTemplate) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: QuickTemplate, newItem: QuickTemplate) = oldItem == newItem
    }
}

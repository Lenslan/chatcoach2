package com.example.chatcoach.ui.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatcoach.R
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.databinding.ItemModelConfigBinding
import com.example.chatcoach.network.models.PlatformConfig

class ModelConfigAdapter(
    private val onItemClick: (LlmConfig) -> Unit,
    private val onSetDefault: (LlmConfig) -> Unit,
    private val onDelete: (LlmConfig) -> Unit,
    private val onDuplicate: (LlmConfig) -> Unit = {}
) : ListAdapter<LlmConfig, ModelConfigAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemModelConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemModelConfigBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(config: LlmConfig) {
            binding.tvConfigName.text = config.name
            binding.tvPlatform.text = PlatformConfig.getPlatformByKey(config.platform)?.name ?: config.platform
            binding.tvModelName.text = config.modelName
            binding.chipDefault.visibility = if (config.isDefault) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onItemClick(config) }
            binding.root.setOnLongClickListener { view ->
                showPopupMenu(view, config)
                true
            }
        }

        private fun showPopupMenu(anchor: View, config: LlmConfig) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.add(0, 1, 0, anchor.context.getString(R.string.set_default))
            popup.menu.add(0, 2, 1, anchor.context.getString(R.string.duplicate_model))
            popup.menu.add(0, 3, 2, anchor.context.getString(R.string.delete_model))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onSetDefault(config); true }
                    2 -> { onDuplicate(config); true }
                    3 -> { onDelete(config); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LlmConfig>() {
        override fun areItemsTheSame(oldItem: LlmConfig, newItem: LlmConfig) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LlmConfig, newItem: LlmConfig) = oldItem == newItem
    }
}

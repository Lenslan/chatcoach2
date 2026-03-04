package com.example.chatcoach.ui.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.databinding.ItemModelConfigBinding
import com.example.chatcoach.network.models.PlatformConfig

class ModelConfigAdapter(
    private val onItemClick: (LlmConfig) -> Unit,
    private val onSetDefault: (LlmConfig) -> Unit,
    private val onDelete: (LlmConfig) -> Unit
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
            binding.root.setOnLongClickListener {
                onSetDefault(config)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<LlmConfig>() {
        override fun areItemsTheSame(oldItem: LlmConfig, newItem: LlmConfig) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: LlmConfig, newItem: LlmConfig) = oldItem == newItem
    }
}

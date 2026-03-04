package com.example.chatcoach.ui.friend

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.databinding.ItemFriendBinding

class FriendAdapter(
    private val onItemClick: (Friend) -> Unit
) : ListAdapter<Friend, FriendAdapter.ViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFriendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: Friend) {
            binding.tvFriendName.text = friend.wechatName
            binding.tvAvatar.text = friend.wechatName.take(1)

            val drawable = binding.tvAvatar.background as? GradientDrawable
                ?: GradientDrawable().apply { shape = GradientDrawable.OVAL }
            drawable.setColor(friend.avatarColor)
            binding.tvAvatar.background = drawable

            if (friend.relationship.isNotBlank()) {
                binding.chipRelationship.text = friend.relationship
                binding.chipRelationship.visibility = android.view.View.VISIBLE
            } else {
                binding.chipRelationship.visibility = android.view.View.GONE
            }

            if (friend.tone.isNotBlank()) {
                binding.chipTone.text = friend.tone
                binding.chipTone.visibility = android.view.View.VISIBLE
            } else {
                binding.chipTone.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onItemClick(friend) }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(oldItem: Friend, newItem: Friend) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Friend, newItem: Friend) = oldItem == newItem
    }
}

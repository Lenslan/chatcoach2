package com.example.chatcoach.ui.analysis

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatcoach.R
import com.example.chatcoach.databinding.FragmentChatAnalysisBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatAnalysisFragment : Fragment() {

    private var _binding: FragmentChatAnalysisBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatAnalysisViewModel by viewModels()
    private val chatAdapter = ChatBubbleAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val friendId = arguments?.getLong("friendId", 0) ?: 0

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        setupChatUI()
        observeData()

        if (friendId > 0) {
            viewModel.loadFriend(friendId)
            viewModel.startAnalysis(friendId)
        }
    }

    private fun setupChatUI() {
        binding.rvChatMessages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatMessages.adapter = chatAdapter

        binding.btnChatSend.setOnClickListener {
            val text = binding.etChatInput.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                binding.etChatInput.text?.clear()
                viewModel.sendChatMessage(text)
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friend.collectLatest { friend ->
                binding.tvFriendName.text = friend?.wechatName ?: ""
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                binding.layoutLoading.visibility = if (loading) View.VISIBLE else View.GONE
                binding.layoutResult.visibility = if (loading) View.GONE else View.VISIBLE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.result.collectLatest { result ->
                result?.let { displayResult(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatMessages.collectLatest { messages ->
                chatAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.rvChatMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isChatLoading.collectLatest { loading ->
                binding.chatLoading.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnChatSend.isEnabled = !loading
            }
        }
    }

    private fun displayResult(result: AnalysisResult) {
        binding.layoutResult.visibility = View.VISIBLE

        binding.tvChatStyle.text = result.chatStyle.ifBlank { "暂无分析结果" }
        binding.tvEmotionTrend.text = result.emotionTrend.ifBlank { "未知" }

        binding.chipGroupTopics.removeAllViews()
        result.topicPreferences.forEach { topic ->
            val chip = Chip(requireContext()).apply {
                text = topic
                isClickable = false
            }
            binding.chipGroupTopics.addView(chip)
        }

        binding.layoutTips.removeAllViews()
        result.communicationTips.forEach { tip ->
            val tv = TextView(requireContext()).apply {
                text = "\u2022 $tip"
                setTextColor(requireContext().getColor(R.color.on_surface))
                textSize = 14f
                setPadding(0, 4, 0, 8)
            }
            binding.layoutTips.addView(tv)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ChatBubbleAdapter : RecyclerView.Adapter<ChatBubbleAdapter.ViewHolder>() {

        private var items: List<ChatBubble> = emptyList()

        fun submitList(newItems: List<ChatBubble>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_bubble, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val container: View = itemView.findViewById(R.id.bubble_container)
            private val tvRole: TextView = itemView.findViewById(R.id.tv_role)
            private val tvContent: TextView = itemView.findViewById(R.id.tv_content)

            fun bind(bubble: ChatBubble) {
                val isUser = bubble.role == "user"
                val params = container.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (isUser) Gravity.END else Gravity.START
                container.layoutParams = params

                tvRole.text = if (isUser) "我" else "AI"
                tvRole.gravity = if (isUser) Gravity.END else Gravity.START
                tvContent.text = bubble.content

                if (isUser) {
                    tvContent.setBackgroundResource(R.drawable.bg_bubble_user)
                } else {
                    tvContent.setBackgroundResource(R.drawable.bg_bubble)
                }
            }
        }
    }
}

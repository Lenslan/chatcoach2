package com.example.chatcoach.ui.analysis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatcoach.R
import com.example.chatcoach.databinding.FragmentChatAnalysisBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatAnalysisFragment : Fragment() {

    private var _binding: FragmentChatAnalysisBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatAnalysisViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val friendId = arguments?.getLong("friendId", 0) ?: 0

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        observeData()

        if (friendId > 0) {
            viewModel.loadFriend(friendId)
            viewModel.startAnalysis(friendId)
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
}

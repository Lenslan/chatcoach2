package com.example.chatcoach.ui.review

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
import com.example.chatcoach.data.db.entity.ChatReview
import com.example.chatcoach.databinding.FragmentChatReviewBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatReviewFragment : Fragment() {

    private var _binding: FragmentChatReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatReviewViewModel by viewModels()
    private val gson = Gson()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val friendId = arguments?.getLong("friendId", 0) ?: 0
        val reviewId = arguments?.getLong("reviewId", 0) ?: 0

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        observeData()

        if (reviewId > 0) {
            viewModel.loadExistingReview(reviewId)
        } else if (friendId > 0) {
            viewModel.loadFriend(friendId)
            viewModel.startReview(friendId)
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
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.reviewResult.collectLatest { review ->
                review?.let { displayReview(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayReview(review: ChatReview) {
        binding.cardScores.visibility = View.VISIBLE
        binding.tvClarityScore.text = review.clarityScore.toString()
        binding.tvToneScore.text = review.toneScore.toString()
        binding.tvEmotionScore.text = review.emotionScore.toString()
        binding.tvTopicScore.text = review.topicScore.toString()

        // Highlights
        try {
            val highlights = gson.fromJson<List<Map<String, Any>>>(
                review.highlights, object : TypeToken<List<Map<String, Any>>>() {}.type
            )
            if (highlights.isNotEmpty()) {
                binding.cardHighlights.visibility = View.VISIBLE
                binding.layoutHighlights.removeAllViews()
                highlights.forEach { item ->
                    val tv = TextView(requireContext()).apply {
                        text = "\u2022 ${item["content"] ?: ""}\n  \u2192 ${item["reason"] ?: ""}"
                        setTextColor(requireContext().getColor(R.color.on_surface))
                        textSize = 13f
                        setPadding(0, 4, 0, 8)
                    }
                    binding.layoutHighlights.addView(tv)
                }
            }
        } catch (_: Exception) {}

        // Improvements
        try {
            val improvements = gson.fromJson<List<Map<String, Any>>>(
                review.improvements, object : TypeToken<List<Map<String, Any>>>() {}.type
            )
            if (improvements.isNotEmpty()) {
                binding.cardImprovements.visibility = View.VISIBLE
                binding.layoutImprovements.removeAllViews()
                improvements.forEach { item ->
                    val tv = TextView(requireContext()).apply {
                        text = "\u2022 原句: ${item["original"] ?: ""}\n  \u2192 建议: ${item["suggested"] ?: ""}\n  原因: ${item["reason"] ?: ""}"
                        setTextColor(requireContext().getColor(R.color.on_surface))
                        textSize = 13f
                        setPadding(0, 4, 0, 8)
                    }
                    binding.layoutImprovements.addView(tv)
                }
            }
        } catch (_: Exception) {}

        // Strategies
        try {
            val strategies = gson.fromJson<List<String>>(
                review.strategies, object : TypeToken<List<String>>() {}.type
            )
            if (strategies.isNotEmpty()) {
                binding.cardStrategies.visibility = View.VISIBLE
                binding.layoutStrategies.removeAllViews()
                strategies.forEach { strategy ->
                    val tv = TextView(requireContext()).apply {
                        text = "\u2022 $strategy"
                        setTextColor(requireContext().getColor(R.color.on_surface))
                        textSize = 13f
                        setPadding(0, 4, 0, 8)
                    }
                    binding.layoutStrategies.addView(tv)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

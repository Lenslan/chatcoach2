package com.example.chatcoach.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatcoach.R
import com.example.chatcoach.databinding.FragmentDashboardBinding
import com.example.chatcoach.service.ChatAccessibilityService
import com.example.chatcoach.ui.friend.FriendAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var friendAdapter: FriendAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        friendAdapter = FriendAdapter { friend ->
            val bundle = Bundle().apply { putLong("friendId", friend.id) }
            findNavController().navigate(R.id.nav_friend_detail, bundle)
        }
        binding.rvRecentFriends.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnToggleService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        binding.cardActionModel.setOnClickListener {
            findNavController().navigate(R.id.nav_model_settings)
        }
        binding.cardActionAnalysis.setOnClickListener {
            // Navigate to analysis - need to select friend first
            findNavController().navigate(R.id.nav_friends)
        }
        binding.cardActionReview.setOnClickListener {
            findNavController().navigate(R.id.nav_friends)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isServiceRunning.collectLatest { running ->
                binding.tvServiceStatus.text = if (running) getString(R.string.service_running) else getString(R.string.service_stopped)
                binding.statusIndicator.setBackgroundResource(
                    if (running) R.drawable.bg_status_dot_active else R.drawable.bg_status_dot
                )
                binding.btnToggleService.text = if (running) "服务运行中" else getString(R.string.btn_enable_service)
                binding.btnToggleService.isEnabled = !running
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friendCount.collectLatest { count ->
                binding.tvFriendCount.text = count.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todayTokens.collectLatest { tokens ->
                binding.tvTodayTokens.text = tokens.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friends.collectLatest { friends ->
                val recent = friends.take(5)
                friendAdapter.submitList(recent)
                binding.tvNoFriends.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                binding.rvRecentFriends.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTokens()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

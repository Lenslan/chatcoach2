package com.example.chatcoach.ui.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatcoach.R
import com.example.chatcoach.databinding.FragmentFriendsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendListFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FriendListViewModel by viewModels()
    private lateinit var adapter: FriendAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = FriendAdapter { friend ->
            val bundle = Bundle().apply { putLong("friendId", friend.id) }
            findNavController().navigate(R.id.nav_friend_detail, bundle)
        }
        binding.rvFriends.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FriendListFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.search(text?.toString() ?: "")
        }
    }

    private fun setupClickListeners() {
        binding.fabAddFriend.setOnClickListener {
            findNavController().navigate(R.id.nav_friend_detail)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friends.collectLatest { friends ->
                adapter.submitList(friends)
                binding.tvEmpty.visibility = if (friends.isEmpty()) View.VISIBLE else View.GONE
                binding.rvFriends.visibility = if (friends.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

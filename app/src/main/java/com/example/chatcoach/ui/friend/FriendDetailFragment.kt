package com.example.chatcoach.ui.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.chatcoach.R
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.databinding.FragmentFriendDetailBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendDetailFragment : Fragment() {

    private var _binding: FragmentFriendDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FriendDetailViewModel by viewModels()
    private var friendId: Long = 0
    private var selectedModelId: Long? = null
    private var modelList: List<LlmConfig> = emptyList()

    private val relationships = listOf("同事", "领导", "客户", "朋友", "恋人", "家人", "长辈")
    private val tones = listOf("正式", "轻松", "亲切", "幽默", "尊敬", "专业")
    private val attitudes = listOf("主动热情", "礼貌克制", "随和自然", "谨慎委婉")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFriendDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        friendId = arguments?.getLong("friendId", 0) ?: 0

        setupChipGroups()
        setupClickListeners()
        observeData()

        if (friendId > 0) {
            binding.tvTitle.text = getString(R.string.edit_friend)
            binding.btnDelete.visibility = View.VISIBLE
            binding.layoutExtraActions.visibility = View.VISIBLE
            viewModel.loadFriend(friendId)
        }
    }

    private fun setupChipGroups() {
        relationships.forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                chipCornerRadius = resources.getDimension(R.dimen.chip_corner_radius)
            }
            binding.chipGroupRelationship.addView(chip)
        }
        tones.forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                chipCornerRadius = resources.getDimension(R.dimen.chip_corner_radius)
            }
            binding.chipGroupTone.addView(chip)
        }
        attitudes.forEach { label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                chipCornerRadius = resources.getDimension(R.dimen.chip_corner_radius)
            }
            binding.chipGroupAttitude.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnDelete.setOnClickListener { showDeleteDialog() }
        binding.btnSave.setOnClickListener { saveFriend() }
        binding.btnAnalysis.setOnClickListener {
            val bundle = Bundle().apply { putLong("friendId", friendId) }
            findNavController().navigate(R.id.nav_chat_analysis, bundle)
        }
        binding.btnReview.setOnClickListener {
            val bundle = Bundle().apply { putLong("friendId", friendId) }
            findNavController().navigate(R.id.nav_chat_review, bundle)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.friend.collectLatest { friend ->
                friend?.let { populateForm(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.models.collectLatest { models ->
                modelList = models
                val modelNames = listOf(getString(R.string.use_default_model)) + models.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, modelNames)
                binding.dropdownModel.setAdapter(adapter)
                binding.dropdownModel.setOnItemClickListener { _, _, position, _ ->
                    selectedModelId = if (position == 0) null else models[position - 1].id
                }
                // Update dropdown text after model list is loaded
                if (selectedModelId != null) {
                    val model = models.find { it.id == selectedModelId }
                    binding.dropdownModel.setText(model?.name ?: getString(R.string.use_default_model), false)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveResult.collectLatest { result ->
                result.onSuccess { id ->
                    if (id == -1L) {
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
                    }
                    findNavController().popBackStack()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateForm(friend: Friend) {
        binding.etName.setText(friend.wechatName)
        binding.etCustomPrompt.setText(friend.customPrompt ?: "")
        binding.etNotes.setText(friend.notes ?: "")
        selectedModelId = friend.preferredModelId

        selectChipByText(binding.chipGroupRelationship, friend.relationship)
        selectChipByText(binding.chipGroupTone, friend.tone)
        selectChipByText(binding.chipGroupAttitude, friend.attitude)

        if (friend.preferredModelId != null) {
            val model = modelList.find { it.id == friend.preferredModelId }
            binding.dropdownModel.setText(model?.name ?: getString(R.string.use_default_model), false)
        } else {
            binding.dropdownModel.setText(getString(R.string.use_default_model), false)
        }
    }

    private fun selectChipByText(chipGroup: com.google.android.material.chip.ChipGroup, text: String) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            if (chip?.text == text) {
                chip.isChecked = true
                break
            }
        }
    }

    private fun getSelectedChipText(chipGroup: com.google.android.material.chip.ChipGroup): String {
        val checkedId = chipGroup.checkedChipId
        if (checkedId == View.NO_ID) return ""
        return chipGroup.findViewById<Chip>(checkedId)?.text?.toString() ?: ""
    }

    private fun saveFriend() {
        val name = binding.etName.text?.toString()?.trim() ?: ""
        if (name.isBlank()) {
            binding.etName.error = "请输入微信名"
            return
        }

        val friend = Friend(
            id = friendId,
            wechatName = name,
            relationship = getSelectedChipText(binding.chipGroupRelationship),
            tone = getSelectedChipText(binding.chipGroupTone),
            attitude = getSelectedChipText(binding.chipGroupAttitude),
            customPrompt = binding.etCustomPrompt.text?.toString()?.trim()?.ifBlank { null },
            notes = binding.etNotes.text?.toString()?.trim()?.ifBlank { null },
            preferredModelId = selectedModelId
        )
        viewModel.saveFriend(friend)
    }

    private fun showDeleteDialog() {
        val friend = viewModel.friend.value ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(getString(R.string.confirm_delete_friend, friend.wechatName))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteFriend(friend)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

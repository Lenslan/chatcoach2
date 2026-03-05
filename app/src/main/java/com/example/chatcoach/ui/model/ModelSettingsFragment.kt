package com.example.chatcoach.ui.model

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatcoach.R
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.databinding.FragmentModelSettingsBinding
import com.example.chatcoach.databinding.DialogModelEditBinding
import com.example.chatcoach.network.models.PlatformConfig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ModelSettingsFragment : Fragment() {

    private var _binding: FragmentModelSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ModelSettingsViewModel by viewModels()
    private lateinit var adapter: ModelConfigAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentModelSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ModelConfigAdapter(
            onItemClick = { config -> showEditDialog(config) },
            onSetDefault = { config -> viewModel.setDefault(config.id) },
            onDelete = { config -> viewModel.deleteConfig(config) }
        )
        binding.rvModels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ModelSettingsFragment.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.fabAddModel.setOnClickListener { showEditDialog(null) }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.configs.collectLatest { configs ->
                adapter.submitList(configs)
                binding.tvEmpty.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
                binding.rvModels.visibility = if (configs.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testResult.collectLatest { result ->
                result.onSuccess { content ->
                    Toast.makeText(requireContext(), "${getString(R.string.test_success)}: $content", Toast.LENGTH_LONG).show()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "${getString(R.string.test_failed)}: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showEditDialog(config: LlmConfig?) {
        val dialogBinding = DialogModelEditBinding.inflate(LayoutInflater.from(requireContext()))
        val platforms = PlatformConfig.getAllPlatforms()
        val platformNames = platforms.map { it.name }
        var selectedPlatform = platforms.find { it.platform == config?.platform } ?: platforms[0]

        val platformAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, platformNames)
        dialogBinding.dropdownPlatform.setAdapter(platformAdapter)

        fun updateModelDropdown(models: List<String>, currentText: String? = null) {
            val modelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, models)
            dialogBinding.dropdownModelName.setAdapter(modelAdapter)
            if (currentText != null) {
                dialogBinding.dropdownModelName.setText(currentText, false)
            }
        }

        fun updateForPlatform(platform: PlatformConfig) {
            selectedPlatform = platform
            val cachedModels = viewModel.getCachedModels(platform.platform)
            val allModels = (cachedModels + platform.defaultModels).distinct()
            if (config == null) {
                dialogBinding.etApiUrl.setText(platform.defaultUrl)
                if (allModels.isNotEmpty()) {
                    updateModelDropdown(allModels, allModels[0])
                }
            } else {
                if (allModels.isNotEmpty()) {
                    updateModelDropdown(allModels)
                }
            }
        }

        fun updateFetchButtonState() {
            dialogBinding.btnFetchModels.isEnabled =
                dialogBinding.etApiUrl.text.toString().isNotBlank()
        }

        dialogBinding.etApiUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateFetchButtonState()
            }
        })

        dialogBinding.dropdownPlatform.setOnItemClickListener { _, _, position, _ ->
            updateForPlatform(platforms[position])
            updateFetchButtonState()
        }

        if (config != null) {
            dialogBinding.etConfigName.setText(config.name)
            dialogBinding.dropdownPlatform.setText(platforms.find { it.platform == config.platform }?.name ?: "", false)
            dialogBinding.etApiUrl.setText(config.apiUrl)
            dialogBinding.etApiKey.setText(config.apiKey)
            dialogBinding.dropdownModelName.setText(config.modelName, false)
            dialogBinding.switchDefault.isChecked = config.isDefault
            val platform = platforms.find { it.platform == config.platform }
            if (platform != null) {
                val cachedModels = viewModel.getCachedModels(platform.platform)
                val allModels = (cachedModels + platform.defaultModels).distinct()
                if (allModels.isNotEmpty()) {
                    updateModelDropdown(allModels)
                }
            }
        } else {
            updateForPlatform(platforms[0])
            dialogBinding.dropdownPlatform.setText(platforms[0].name, false)
        }

        updateFetchButtonState()

        // Fetch models button
        dialogBinding.btnFetchModels.setOnClickListener {
            val apiUrl = dialogBinding.etApiUrl.text.toString()
            val apiKey = dialogBinding.etApiKey.text.toString()
            viewModel.fetchModels(apiUrl, apiKey, selectedPlatform.platform)
        }

        // Collect fetch models state
        val fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFetchingModels.collectLatest { isFetching ->
                dialogBinding.btnFetchModels.isEnabled = !isFetching && dialogBinding.etApiUrl.text.toString().isNotBlank()
                dialogBinding.progressFetchModels.visibility = if (isFetching) View.VISIBLE else View.GONE
            }
        }

        val fetchResultJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fetchModelsResult.collectLatest { result ->
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.fetch_models_empty), Toast.LENGTH_SHORT).show()
                        dialogBinding.btnShowDebug.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.fetch_models_success, models.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        val currentDefault = selectedPlatform.defaultModels
                        val allModels = (models + currentDefault).distinct()
                        updateModelDropdown(allModels, models[0])
                        dialogBinding.dropdownModelName.showDropDown()
                    }
                }.onFailure { e ->
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.fetch_models_failed)}: ${e.message?.take(100)}",
                        Toast.LENGTH_LONG
                    ).show()
                    dialogBinding.btnShowDebug.visibility = View.VISIBLE
                }
            }
        }

        // Test button
        dialogBinding.btnTest.setOnClickListener {
            val testConfig = LlmConfig(
                id = config?.id ?: 0,
                name = dialogBinding.etConfigName.text.toString(),
                platform = selectedPlatform.platform,
                apiUrl = dialogBinding.etApiUrl.text.toString(),
                apiKey = dialogBinding.etApiKey.text.toString(),
                modelName = dialogBinding.dropdownModelName.text.toString()
            )
            dialogBinding.tvTestResult.visibility = View.VISIBLE
            dialogBinding.tvTestResult.text = getString(R.string.testing)
            dialogBinding.tvTestResult.setTextColor(requireContext().getColor(R.color.on_surface_variant))
            viewModel.testConnection(testConfig)
        }

        // Collect test result in dialog context
        val testResultJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.testResult.collectLatest { result ->
                dialogBinding.tvTestResult.visibility = View.VISIBLE
                result.onSuccess { content ->
                    dialogBinding.tvTestResult.text = "${getString(R.string.test_success)}: $content"
                }.onFailure { e ->
                    dialogBinding.tvTestResult.text = "${getString(R.string.test_failed)}: ${e.message?.take(100)}"
                    dialogBinding.btnShowDebug.visibility = View.VISIBLE
                }
            }
        }

        // Debug info toggle
        dialogBinding.btnShowDebug.setOnClickListener {
            if (dialogBinding.tvDebugInfo.visibility == View.VISIBLE) {
                dialogBinding.tvDebugInfo.visibility = View.GONE
            } else {
                val debugLog = viewModel.getDebugLog()
                dialogBinding.tvDebugInfo.text = debugLog ?: "(无调试信息)"
                dialogBinding.tvDebugInfo.visibility = View.VISIBLE
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (config != null) getString(R.string.edit_model) else getString(R.string.add_model))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newConfig = LlmConfig(
                    id = config?.id ?: 0,
                    name = dialogBinding.etConfigName.text.toString().ifBlank { selectedPlatform.name },
                    platform = selectedPlatform.platform,
                    apiUrl = dialogBinding.etApiUrl.text.toString(),
                    apiKey = dialogBinding.etApiKey.text.toString(),
                    modelName = dialogBinding.dropdownModelName.text.toString(),
                    isDefault = dialogBinding.switchDefault.isChecked,
                    createdAt = config?.createdAt ?: System.currentTimeMillis()
                )
                viewModel.saveConfig(newConfig)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener {
                fetchJob.cancel()
                fetchResultJob.cancel()
                testResultJob.cancel()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

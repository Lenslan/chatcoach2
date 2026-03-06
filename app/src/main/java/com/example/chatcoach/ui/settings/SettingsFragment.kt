package com.example.chatcoach.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.chatcoach.R
import com.example.chatcoach.databinding.FragmentSettingsBinding
import com.example.chatcoach.service.ChatAccessibilityService
import com.example.chatcoach.service.FloatingWindowService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = viewModel.preferences
        setupFloatingSettings(prefs)
        setupContextSettings(prefs)
        setupDataSettings(prefs)
        setupShizukuSettings(prefs)
        observeData()
    }

    private fun setupFloatingSettings(prefs: com.example.chatcoach.data.preferences.AppPreferences) {
        binding.switchFloating.isChecked = prefs.isFloatingWindowEnabled
        binding.switchFloating.setOnCheckedChangeListener { _, checked ->
            prefs.isFloatingWindowEnabled = checked
        }
        binding.sliderOpacity.value = prefs.floatingWindowOpacity
        binding.sliderOpacity.addOnChangeListener { _, value, _ ->
            prefs.floatingWindowOpacity = value
            FloatingWindowService.instance?.updateOpacity(value)
        }
    }

    private fun setupContextSettings(prefs: com.example.chatcoach.data.preferences.AppPreferences) {
        binding.switchAutoTrigger.isChecked = prefs.isAutoTriggerEnabled
        binding.switchAutoTrigger.setOnCheckedChangeListener { _, checked ->
            prefs.isAutoTriggerEnabled = checked
        }
        binding.sliderMaxContext.value = prefs.maxContextMessages.toFloat()
        binding.tvMaxContext.text = "${prefs.maxContextMessages} 条"
        binding.sliderMaxContext.addOnChangeListener { _, value, _ ->
            prefs.maxContextMessages = value.toInt()
            binding.tvMaxContext.text = "${value.toInt()} 条"
        }
        binding.sliderSummaryThreshold.value = prefs.summaryThreshold.toFloat()
        binding.tvSummaryThreshold.text = "${prefs.summaryThreshold} 条"
        binding.sliderSummaryThreshold.addOnChangeListener { _, value, _ ->
            prefs.summaryThreshold = value.toInt()
            binding.tvSummaryThreshold.text = "${value.toInt()} 条"
        }
    }

    private fun setupDataSettings(prefs: com.example.chatcoach.data.preferences.AppPreferences) {
        binding.sliderCacheDays.value = prefs.cacheDays.toFloat()
        binding.tvCacheDays.text = "${prefs.cacheDays} 天"
        binding.sliderCacheDays.addOnChangeListener { _, value, _ ->
            prefs.cacheDays = value.toInt()
            binding.tvCacheDays.text = "${value.toInt()} 天"
        }
        binding.btnClearCache.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("清除缓存")
                .setMessage("确定要清除所有聊天记录缓存吗？此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除") { _, _ ->
                    viewModel.clearCache()
                    Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun setupShizukuSettings(prefs: com.example.chatcoach.data.preferences.AppPreferences) {
        binding.switchShizuku.isChecked = prefs.isShizukuModeEnabled
        binding.switchShizuku.setOnCheckedChangeListener { _, checked ->
            prefs.isShizukuModeEnabled = checked
            // Notify the running accessibility service to bind/unbind Shizuku immediately
            ChatAccessibilityService.instance?.onShizukuModeChanged(checked)
            updateShizukuStatus()
        }
        updateShizukuStatus()
    }

    private fun updateShizukuStatus() {
        val statusText = try {
            val prefs = viewModel.preferences
            if (!prefs.isShizukuModeEnabled) {
                getString(R.string.shizuku_status_unknown)
            } else if (!rikka.shizuku.Shizuku.pingBinder()) {
                getString(R.string.shizuku_status_not_running)
            } else {
                val available = rikka.shizuku.Shizuku.checkSelfPermission() ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (available) {
                    getString(R.string.shizuku_status_connected)
                } else {
                    getString(R.string.shizuku_status_no_permission)
                }
            }
        } catch (e: IllegalStateException) {
            getString(R.string.shizuku_status_not_running)
        } catch (_: Exception) {
            getString(R.string.shizuku_status_not_installed)
        }
        binding.tvShizukuStatus.text = statusText
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todayTokens.collectLatest { tokens ->
                binding.tvTodayTokens.text = tokens.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalTokens.collectLatest { tokens ->
                binding.tvTotalTokens.text = tokens.toString()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStats()
        updateShizukuStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

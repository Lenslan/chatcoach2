package com.example.chatcoach.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.data.repository.LlmConfigRepository
import com.example.chatcoach.network.LlmApiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ModelSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val llmConfigRepo = LlmConfigRepository((app as ChatCoachApp).database.llmConfigDao())
    private val llmApiService = LlmApiService()

    val configs: StateFlow<List<LlmConfig>> = llmConfigRepo.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testResult = MutableSharedFlow<Result<String>>()
    val testResult: SharedFlow<Result<String>> = _testResult

    fun saveConfig(config: LlmConfig) {
        viewModelScope.launch {
            if (config.id == 0L) {
                llmConfigRepo.addConfig(config)
            } else {
                llmConfigRepo.updateConfig(config)
            }
            if (config.isDefault) {
                config.id.takeIf { it != 0L }?.let { llmConfigRepo.setDefault(it) }
            }
        }
    }

    fun deleteConfig(config: LlmConfig) {
        viewModelScope.launch { llmConfigRepo.deleteConfig(config) }
    }

    fun testConnection(config: LlmConfig) {
        viewModelScope.launch {
            _testResult.emit(llmApiService.testConnection(config))
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { llmConfigRepo.setDefault(id) }
    }
}

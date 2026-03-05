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
    private val preferences = (app as ChatCoachApp).preferences

    val configs: StateFlow<List<LlmConfig>> = llmConfigRepo.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _testResult = MutableSharedFlow<Result<String>>()
    val testResult: SharedFlow<Result<String>> = _testResult

    private val _fetchModelsResult = MutableSharedFlow<Result<List<String>>>()
    val fetchModelsResult: SharedFlow<Result<List<String>>> = _fetchModelsResult

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels

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
            val result = llmApiService.testConnection(config)
            if (result.isFailure) {
                llmApiService.lastDebugInfo?.let { debugInfo ->
                    preferences.saveDebugLog(debugInfo.toJson())
                }
            }
            _testResult.emit(result)
        }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { llmConfigRepo.setDefault(id) }
    }

    fun fetchModels(apiUrl: String, apiKey: String, platform: String) {
        viewModelScope.launch {
            _isFetchingModels.value = true
            val (result, debugInfo) = llmApiService.fetchModels(apiUrl, apiKey, platform)
            debugInfo?.let { preferences.saveDebugLog(it.toJson()) }
            if (result.isSuccess) {
                val models = result.getOrDefault(emptyList())
                if (models.isNotEmpty()) {
                    preferences.saveCachedModels(platform, models)
                }
            }
            _fetchModelsResult.emit(result)
            _isFetchingModels.value = false
        }
    }

    fun getCachedModels(platform: String): List<String> {
        return preferences.getCachedModels(platform)
    }

    fun getDebugLog(): String? {
        return preferences.getDebugLog()
    }
}

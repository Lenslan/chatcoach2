package com.example.chatcoach.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.repository.ChatMessageRepository
import com.example.chatcoach.data.repository.TokenUsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val chatCoachApp = app as ChatCoachApp
    private val tokenRepo = TokenUsageRepository(chatCoachApp.database.tokenUsageDao())
    private val chatRepo = ChatMessageRepository(chatCoachApp.database.chatMessageDao())
    val preferences = chatCoachApp.preferences

    private val _todayTokens = MutableStateFlow(0L)
    val todayTokens: StateFlow<Long> = _todayTokens

    private val _totalTokens = MutableStateFlow(0L)
    val totalTokens: StateFlow<Long> = _totalTokens

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _todayTokens.value = tokenRepo.getTodayTokens()
            _totalTokens.value = tokenRepo.getTotalTokens()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            chatRepo.cleanOldMessages(0)
        }
    }
}

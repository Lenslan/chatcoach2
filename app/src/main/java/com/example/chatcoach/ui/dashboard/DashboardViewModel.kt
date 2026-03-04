package com.example.chatcoach.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.repository.FriendRepository
import com.example.chatcoach.data.repository.TokenUsageRepository
import com.example.chatcoach.service.ChatAccessibilityService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as ChatCoachApp).database
    private val friendRepo = FriendRepository(database.friendDao())
    private val tokenRepo = TokenUsageRepository(database.tokenUsageDao())

    val friendCount = friendRepo.getAllFriends().map { it.size }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    private val _todayTokens = MutableStateFlow(0L)
    val todayTokens: StateFlow<Long> = _todayTokens

    val isServiceRunning: StateFlow<Boolean> = ChatAccessibilityService.serviceStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val friends = friendRepo.getAllFriends().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        loadTokens()
    }

    fun loadTokens() {
        viewModelScope.launch {
            _todayTokens.value = tokenRepo.getTodayTokens()
        }
    }
}

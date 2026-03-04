package com.example.chatcoach.ui.friend

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.data.repository.FriendRepository
import com.example.chatcoach.data.repository.LlmConfigRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FriendDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as ChatCoachApp).database
    private val friendRepo = FriendRepository(database.friendDao())
    private val llmConfigRepo = LlmConfigRepository(database.llmConfigDao())

    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend

    private val _saveResult = MutableSharedFlow<Result<Long>>()
    val saveResult: SharedFlow<Result<Long>> = _saveResult

    val models: StateFlow<List<LlmConfig>> = llmConfigRepo.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadFriend(id: Long) {
        viewModelScope.launch {
            _friend.value = friendRepo.getFriendById(id)
        }
    }

    fun saveFriend(friend: Friend) {
        viewModelScope.launch {
            try {
                val id = if (friend.id == 0L) {
                    friendRepo.addFriend(friend)
                } else {
                    friendRepo.updateFriend(friend)
                    friend.id
                }
                _saveResult.emit(Result.success(id))
            } catch (e: Exception) {
                _saveResult.emit(Result.failure(e))
            }
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            friendRepo.deleteFriend(friend)
            _saveResult.emit(Result.success(-1))
        }
    }
}

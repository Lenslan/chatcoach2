package com.example.chatcoach.ui.friend

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.data.repository.FriendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FriendListViewModel(app: Application) : AndroidViewModel(app) {
    private val friendRepo = FriendRepository((app as ChatCoachApp).database.friendDao())

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val friends: StateFlow<List<Friend>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) friendRepo.getAllFriends()
        else friendRepo.searchFriends(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch { friendRepo.deleteFriend(friend) }
    }
}

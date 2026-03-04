package com.example.chatcoach.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.ChatReview
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.data.repository.ChatMessageRepository
import com.example.chatcoach.data.repository.ChatReviewRepository
import com.example.chatcoach.data.repository.FriendRepository
import com.example.chatcoach.data.repository.LlmConfigRepository
import com.example.chatcoach.network.LlmApiService
import com.example.chatcoach.network.models.MessageItem
import com.example.chatcoach.service.PromptBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as ChatCoachApp).database
    private val friendRepo = FriendRepository(database.friendDao())
    private val chatMessageRepo = ChatMessageRepository(database.chatMessageDao())
    private val chatReviewRepo = ChatReviewRepository(database.chatReviewDao())
    private val llmConfigRepo = LlmConfigRepository(database.llmConfigDao())
    private val llmService = LlmApiService()
    private val gson = Gson()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _reviewResult = MutableStateFlow<ChatReview?>(null)
    val reviewResult: StateFlow<ChatReview?> = _reviewResult

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend

    fun loadFriend(friendId: Long) {
        viewModelScope.launch {
            _friend.value = friendRepo.getFriendById(friendId)
        }
    }

    fun startReview(friendId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val friend = friendRepo.getFriendById(friendId) ?: run {
                    _error.emit("好友不存在")
                    return@launch
                }

                val config = if (friend.preferredModelId != null) {
                    llmConfigRepo.getConfigById(friend.preferredModelId)
                } else {
                    llmConfigRepo.getDefaultConfig()
                } ?: run {
                    _error.emit("请先配置大模型")
                    return@launch
                }

                val messages = chatMessageRepo.getRecentMessages(friend.wechatName, 50).reversed()
                if (messages.isEmpty()) {
                    _error.emit("没有找到聊天记录")
                    return@launch
                }

                val prompt = PromptBuilder.buildReviewPrompt(friend, messages)
                val response = llmService.sendRequest(
                    config,
                    listOf(MessageItem.system(prompt), MessageItem.user("请开始复盘分析"))
                )

                val content = response.choices?.firstOrNull()?.message?.content ?: ""
                val review = parseReviewResult(content, friend, messages.size)
                chatReviewRepo.addReview(review)
                _reviewResult.value = review
            } catch (e: Exception) {
                _error.emit("复盘失败: ${e.message?.take(100)}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadExistingReview(reviewId: Long) {
        viewModelScope.launch {
            _reviewResult.value = chatReviewRepo.getById(reviewId)
        }
    }

    private fun parseReviewResult(content: String, friend: Friend, messageCount: Int): ChatReview {
        return try {
            // Try to extract JSON from the content
            val jsonStr = content.let {
                val start = it.indexOf('{')
                val end = it.lastIndexOf('}')
                if (start >= 0 && end > start) it.substring(start, end + 1) else it
            }
            val map = gson.fromJson<Map<String, Any>>(jsonStr, object : TypeToken<Map<String, Any>>() {}.type)

            ChatReview(
                friendId = friend.id,
                startTime = System.currentTimeMillis() - 3600_000,
                endTime = System.currentTimeMillis(),
                messageCount = messageCount,
                clarityScore = (map["clarityScore"] as? Double)?.toInt() ?: 0,
                toneScore = (map["toneScore"] as? Double)?.toInt() ?: 0,
                emotionScore = (map["emotionScore"] as? Double)?.toInt() ?: 0,
                topicScore = (map["topicScore"] as? Double)?.toInt() ?: 0,
                highlights = gson.toJson(map["highlights"] ?: emptyList<Any>()),
                improvements = gson.toJson(map["improvements"] ?: emptyList<Any>()),
                strategies = gson.toJson(map["strategies"] ?: emptyList<Any>())
            )
        } catch (e: Exception) {
            ChatReview(
                friendId = friend.id,
                startTime = System.currentTimeMillis() - 3600_000,
                endTime = System.currentTimeMillis(),
                messageCount = messageCount,
                highlights = "[]",
                improvements = "[]",
                strategies = gson.toJson(listOf(content.take(500)))
            )
        }
    }
}

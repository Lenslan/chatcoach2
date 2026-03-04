package com.example.chatcoach.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.data.repository.ChatMessageRepository
import com.example.chatcoach.data.repository.FriendRepository
import com.example.chatcoach.data.repository.LlmConfigRepository
import com.example.chatcoach.network.LlmApiService
import com.example.chatcoach.network.models.MessageItem
import com.example.chatcoach.service.PromptBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AnalysisResult(
    val chatStyle: String = "",
    val emotionTrend: String = "",
    val topicPreferences: List<String> = emptyList(),
    val communicationTips: List<String> = emptyList()
)

class ChatAnalysisViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as ChatCoachApp).database
    private val friendRepo = FriendRepository(database.friendDao())
    private val chatRepo = ChatMessageRepository(database.chatMessageDao())
    private val llmConfigRepo = LlmConfigRepository(database.llmConfigDao())
    private val llmService = LlmApiService()
    private val gson = Gson()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    val result: StateFlow<AnalysisResult?> = _result

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend

    fun loadFriend(friendId: Long) {
        viewModelScope.launch {
            _friend.value = friendRepo.getFriendById(friendId)
        }
    }

    fun startAnalysis(friendId: Long) {
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

                val messages = chatRepo.getRecentMessages(friend.wechatName, 50).reversed()
                if (messages.isEmpty()) {
                    _error.emit("没有找到聊天记录")
                    return@launch
                }

                val prompt = PromptBuilder.buildAnalysisPrompt(friend, messages)
                val response = llmService.sendRequest(
                    config,
                    listOf(MessageItem.user(prompt))
                )

                val content = response.choices?.firstOrNull()?.message?.content ?: ""
                _result.value = parseAnalysisResult(content)
            } catch (e: Exception) {
                _error.emit("分析失败: ${e.message?.take(100)}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseAnalysisResult(content: String): AnalysisResult {
        return try {
            val jsonStr = content.let {
                val start = it.indexOf('{')
                val end = it.lastIndexOf('}')
                if (start >= 0 && end > start) it.substring(start, end + 1) else it
            }
            val map = gson.fromJson<Map<String, Any>>(jsonStr, object : TypeToken<Map<String, Any>>() {}.type)
            @Suppress("UNCHECKED_CAST")
            AnalysisResult(
                chatStyle = map["chatStyle"] as? String ?: "",
                emotionTrend = map["emotionTrend"] as? String ?: "",
                topicPreferences = (map["topicPreferences"] as? List<String>) ?: emptyList(),
                communicationTips = (map["communicationTips"] as? List<String>) ?: emptyList()
            )
        } catch (e: Exception) {
            AnalysisResult(chatStyle = content.take(500))
        }
    }
}

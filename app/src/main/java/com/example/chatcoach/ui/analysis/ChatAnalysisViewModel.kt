package com.example.chatcoach.ui.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.ChatMessage
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

data class ChatBubble(val role: String, val content: String)

data class AnalyzedMessage(val sender: String, val content: String)

/**
 * JSON structure cached in SharedPreferences per friend.
 */
private data class AnalysisCacheData(
    val result: AnalysisResult,
    val analyzedMessages: List<AnalyzedMessage>,
    val chatBubbles: List<ChatBubble>,
    val timestamp: Long
)

class ChatAnalysisViewModel(app: Application) : AndroidViewModel(app) {
    private val database = (app as ChatCoachApp).database
    private val friendRepo = FriendRepository(database.friendDao())
    private val chatRepo = ChatMessageRepository(database.chatMessageDao())
    private val llmConfigRepo = LlmConfigRepository(database.llmConfigDao())
    private val llmService = LlmApiService()
    private val preferences = (app as ChatCoachApp).preferences
    private val gson = Gson()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _result = MutableStateFlow<AnalysisResult?>(null)
    val result: StateFlow<AnalysisResult?> = _result

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error

    private val _friend = MutableStateFlow<Friend?>(null)
    val friend: StateFlow<Friend?> = _friend

    private val _chatMessages = MutableStateFlow<List<ChatBubble>>(emptyList())
    val chatMessages: StateFlow<List<ChatBubble>> = _chatMessages

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading

    private val _analyzedMessages = MutableStateFlow<List<AnalyzedMessage>>(emptyList())
    val analyzedMessages: StateFlow<List<AnalyzedMessage>> = _analyzedMessages

    private val _cleared = MutableSharedFlow<Unit>()
    val cleared: SharedFlow<Unit> = _cleared

    private var currentFriendId: Long = 0

    fun loadFriend(friendId: Long) {
        currentFriendId = friendId
        viewModelScope.launch {
            _friend.value = friendRepo.getFriendById(friendId)
        }
    }

    fun loadCachedAnalysis(friendId: Long) {
        val json = preferences.getAnalysisCache(friendId) ?: return
        try {
            val cache = gson.fromJson(json, AnalysisCacheData::class.java)
            _result.value = cache.result
            _analyzedMessages.value = cache.analyzedMessages
            _chatMessages.value = cache.chatBubbles
        } catch (_: Exception) {
            // Cache corrupt, ignore
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
                val analysisResult = parseAnalysisResult(content)
                _result.value = analysisResult

                // Store the analyzed messages
                val analyzed = messages.map { msg ->
                    val sender = if (msg.sender == ChatMessage.SENDER_ME) "我" else friend.wechatName
                    AnalyzedMessage(sender = sender, content = msg.getDisplayContent())
                }
                _analyzedMessages.value = analyzed

                // Clear previous chat bubbles for new analysis
                _chatMessages.value = emptyList()

                // Persist to cache
                saveCache(friendId)
            } catch (e: Exception) {
                _error.emit("分析失败: ${e.message?.take(100)}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearAnalysis(friendId: Long) {
        preferences.removeAnalysisCache(friendId)
        _result.value = null
        _analyzedMessages.value = emptyList()
        _chatMessages.value = emptyList()
        viewModelScope.launch {
            _cleared.emit(Unit)
        }
    }

    fun sendChatMessage(question: String) {
        val currentResult = _result.value ?: return
        val currentFriend = _friend.value

        viewModelScope.launch {
            _isChatLoading.value = true
            _chatMessages.value = _chatMessages.value + ChatBubble("user", question)

            try {
                val config = if (currentFriend?.preferredModelId != null) {
                    llmConfigRepo.getConfigById(currentFriend.preferredModelId)
                } else {
                    llmConfigRepo.getDefaultConfig()
                } ?: run {
                    _error.emit("请先配置大模型")
                    _isChatLoading.value = false
                    return@launch
                }

                val systemPrompt = buildString {
                    appendLine("你是一位社交沟通分析助手。以下是对用户与好友聊天记录的分析结果，请基于这些信息回答用户的后续问题。")
                    appendLine()
                    if (currentFriend != null) {
                        appendLine("[好友信息]")
                        appendLine("- 好友名称：${currentFriend.wechatName}")
                        if (currentFriend.relationship.isNotBlank()) appendLine("- 关系：${currentFriend.relationship}")
                        if (currentFriend.tone.isNotBlank()) appendLine("- 语气：${currentFriend.tone}")
                        if (currentFriend.attitude.isNotBlank()) appendLine("- 态度：${currentFriend.attitude}")
                        appendLine()
                    }
                    appendLine("[分析结果]")
                    appendLine("- 聊天风格：${currentResult.chatStyle}")
                    appendLine("- 情绪倾向：${currentResult.emotionTrend}")
                    appendLine("- 话题偏好：${currentResult.topicPreferences.joinToString("、")}")
                    appendLine("- 沟通建议：${currentResult.communicationTips.joinToString("；")}")
                }

                val apiMessages = mutableListOf<MessageItem>()
                apiMessages.add(MessageItem.system(systemPrompt))
                for (bubble in _chatMessages.value) {
                    apiMessages.add(MessageItem(role = bubble.role, content = bubble.content))
                }

                val response = llmService.sendRequest(config, apiMessages)
                val reply = response.choices?.firstOrNull()?.message?.content ?: "无回复"
                _chatMessages.value = _chatMessages.value + ChatBubble("assistant", reply)

                // Persist chat bubbles to cache
                saveCache(currentFriendId)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatBubble("assistant", "请求失败: ${e.message?.take(100)}")
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    private fun saveCache(friendId: Long) {
        val result = _result.value ?: return
        val cache = AnalysisCacheData(
            result = result,
            analyzedMessages = _analyzedMessages.value,
            chatBubbles = _chatMessages.value,
            timestamp = System.currentTimeMillis()
        )
        preferences.saveAnalysisCache(friendId, gson.toJson(cache))
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

package com.example.chatcoach.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class ChatAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastMessages = mutableListOf<String>()
    private var currentChatName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            packageNames = arrayOf(WECHAT_PACKAGE)
            notificationTimeout = 200
        }
        instance = this
        _serviceStatus.tryEmit(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != WECHAT_PACKAGE) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: return
                if (className == WECHAT_CHAT_UI || className == WECHAT_CHAT_UI_ALT) {
                    _isInChatPage.tryEmit(true)
                    extractChatInfo()
                } else {
                    _isInChatPage.tryEmit(false)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (_isInChatPage.replayCache.lastOrNull() == true) {
                    extractChatInfo()
                }
            }
        }
    }

    private fun extractChatInfo() {
        val rootNode = rootInActiveWindow ?: return
        try {
            // Extract chat contact name from title bar
            val chatName = extractChatName(rootNode)
            if (chatName != null && chatName != currentChatName) {
                currentChatName = chatName
                lastMessages.clear()
                _currentFriendName.tryEmit(chatName)
            }

            // Extract messages
            val messages = extractMessages(rootNode)
            if (messages.isNotEmpty() && messages != lastMessages) {
                lastMessages = messages.toMutableList()
                val chatMessages = parseToChatMessages(messages, currentChatName ?: return)
                serviceScope.launch {
                    _newMessages.emit(chatMessages)
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun extractChatName(root: AccessibilityNodeInfo): String? {
        // Try to find the chat name in the title bar
        // WeChat uses different view IDs across versions, so we try multiple approaches
        val titleNodes = root.findAccessibilityNodeInfosByViewId("$WECHAT_PACKAGE:id/action_bar_title_layout")
        if (titleNodes.isNotEmpty()) {
            val titleNode = titleNodes[0]
            for (i in 0 until titleNode.childCount) {
                val child = titleNode.getChild(i)
                val text = child?.text?.toString()
                child?.recycle()
                if (!text.isNullOrBlank()) return text
            }
            titleNode.recycle()
        }

        // Fallback: try other common IDs
        val altTitleNodes = root.findAccessibilityNodeInfosByViewId("$WECHAT_PACKAGE:id/title_name")
        if (altTitleNodes.isNotEmpty()) {
            val text = altTitleNodes[0].text?.toString()
            altTitleNodes[0].recycle()
            if (!text.isNullOrBlank()) return text
        }

        // Fallback: look for the first TextView in top area
        return findTitleInTopArea(root)
    }

    private fun findTitleInTopArea(node: AccessibilityNodeInfo): String? {
        if (node.className?.toString() == "android.widget.TextView") {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && text.length in 1..20) {
                return text
            }
        }
        for (i in 0 until minOf(node.childCount, 5)) {
            val child = node.getChild(i) ?: continue
            val result = findTitleInTopArea(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun extractMessages(root: AccessibilityNodeInfo): List<String> {
        val messages = mutableListOf<String>()

        // Find the message list view
        val listNodes = root.findAccessibilityNodeInfosByViewId("$WECHAT_PACKAGE:id/chatting_content_layout")
        val listNode = if (listNodes.isNotEmpty()) listNodes[0] else root

        // Recursively find all message text views
        collectMessages(listNode, messages)

        if (listNodes.isNotEmpty()) listNode.recycle()
        return messages
    }

    private fun collectMessages(node: AccessibilityNodeInfo, messages: MutableList<String>) {
        val text = node.text?.toString()
        val className = node.className?.toString() ?: ""

        if (className == "android.widget.TextView" && !text.isNullOrBlank()) {
            // Filter out system UI text
            if (text.length > 1 && !isSystemText(text)) {
                messages.add(text)
            }
        }

        // Check for voice message nodes that have been converted to text
        val desc = node.contentDescription?.toString()
        if (desc != null && desc.contains("语音") && !text.isNullOrBlank()) {
            // Mark as voice transcript
            messages.add("[语音] $text")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMessages(child, messages)
            child.recycle()
        }
    }

    private fun isSystemText(text: String): Boolean {
        val systemPatterns = listOf(
            "发送", "按住", "说话", "更多", "表情", "返回", "微信",
            "语音通话", "视频通话", "以下是新消息", "查看更多消息"
        )
        return systemPatterns.any { text == it } || text.matches(Regex("\\d{1,2}:\\d{2}"))
    }

    private fun parseToChatMessages(rawMessages: List<String>, friendName: String): List<ChatMessage> {
        // Simple heuristic: messages from the right side are mine
        // In accessibility, we can't easily determine sender without position info
        // So we use a simple pattern: alternate or use context clues
        return rawMessages.mapIndexed { index, content ->
            val isVoice = content.startsWith("[语音] ")
            val actualContent = if (isVoice) content.removePrefix("[语音] ") else content
            ChatMessage(
                friendName = friendName,
                sender = if (index % 2 == 0) ChatMessage.SENDER_FRIEND else ChatMessage.SENDER_ME,
                content = actualContent,
                messageType = if (isVoice) ChatMessage.TYPE_VOICE else ChatMessage.TYPE_TEXT,
                voiceTranscript = if (isVoice) actualContent else null,
                timestamp = System.currentTimeMillis() - (rawMessages.size - index) * 1000L
            )
        }
    }

    override fun onInterrupt() {
        instance = null
        _serviceStatus.tryEmit(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        _serviceStatus.tryEmit(false)
    }

    companion object {
        const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val WECHAT_CHAT_UI = "com.tencent.mm.ui.LauncherUI"
        private const val WECHAT_CHAT_UI_ALT = "com.tencent.mm.ui.chatting.ChattingUI"

        var instance: ChatAccessibilityService? = null
            private set

        private val _serviceStatus = MutableSharedFlow<Boolean>(replay = 1)
        val serviceStatus: SharedFlow<Boolean> = _serviceStatus

        private val _isInChatPage = MutableSharedFlow<Boolean>(replay = 1)
        val isInChatPage: SharedFlow<Boolean> = _isInChatPage

        private val _currentFriendName = MutableSharedFlow<String>(replay = 1)
        val currentFriendName: SharedFlow<String> = _currentFriendName

        private val _newMessages = MutableSharedFlow<List<ChatMessage>>()
        val newMessages: SharedFlow<List<ChatMessage>> = _newMessages

        fun isServiceRunning(): Boolean = instance != null
    }
}

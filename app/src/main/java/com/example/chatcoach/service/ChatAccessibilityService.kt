package com.example.chatcoach.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.data.db.entity.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class ChatAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatAccessibility"
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

        private val _debugLog = MutableSharedFlow<String>(replay = 1)
        val debugLog: SharedFlow<String> = _debugLog

        fun isServiceRunning(): Boolean = instance != null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastMessages = mutableListOf<String>()
    private var currentChatName: String? = null
    private var shizukuExtractor: ShizukuChatExtractor? = null
    private val isShizukuDumping = AtomicBoolean(false)

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

        initShizuku()
    }

    private fun initShizuku() {
        try {
            if (shizukuExtractor == null) {
                shizukuExtractor = ShizukuChatExtractor()
            }
            val prefs = ChatCoachApp.instance.preferences
            if (prefs.isShizukuModeEnabled) {
                shizukuExtractor?.registerListeners()
                shizukuExtractor?.bindService()
                Log.d(TAG, "Shizuku initialized with listeners")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku init failed", e)
        }
    }

    /**
     * Called from SettingsFragment when the Shizuku toggle changes.
     */
    fun onShizukuModeChanged(enabled: Boolean) {
        if (enabled) {
            if (shizukuExtractor == null) {
                shizukuExtractor = ShizukuChatExtractor()
            }
            shizukuExtractor?.registerListeners()
            shizukuExtractor?.bindService()
            Log.d(TAG, "Shizuku bound from settings toggle")
        } else {
            shizukuExtractor?.unbindService()
            shizukuExtractor?.unregisterListeners()
            Log.d(TAG, "Shizuku unbound from settings toggle")
        }
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
        val prefs = ChatCoachApp.instance.preferences
        val extractor = shizukuExtractor
        if (prefs.isShizukuModeEnabled && extractor != null && extractor.isAvailable()) {
            extractChatInfoViaShizuku(extractor)
        } else {
            extractChatInfoViaAccessibility()
        }
    }

    private fun extractChatInfoViaShizuku(extractor: ShizukuChatExtractor) {
        // Prevent concurrent uiautomator dump calls
        if (!isShizukuDumping.compareAndSet(false, true)) return

        serviceScope.launch(Dispatchers.IO) {
            try {
                val xml = extractor.dumpUI()
                if (xml == null) {
                    emitDebug("[Shizuku] dumpUI returned null — uiautomator dump may have failed")
                    return@launch
                }
                emitDebug("[Shizuku] dumpUI success, xml length=${xml.length}")

                val chatName = extractor.extractChatName(xml)
                emitDebug("[Shizuku] extractChatName=$chatName")

                if (chatName != null) {
                    withContext(Dispatchers.Main) {
                        if (chatName != currentChatName) {
                            currentChatName = chatName
                            lastMessages.clear()
                            _currentFriendName.tryEmit(chatName)
                        }
                    }

                    val messages = extractor.extractMessages(xml, chatName)
                    emitDebug("[Shizuku] extractMessages count=${messages.size}")

                    if (messages.isNotEmpty()) {
                        val messageTexts = messages.map { it.content }
                        withContext(Dispatchers.Main) {
                            if (messageTexts != lastMessages) {
                                lastMessages = messageTexts.toMutableList()
                                _newMessages.emit(messages)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku extraction failed", e)
                emitDebug("[Shizuku] ERROR: ${e.message}")
            } finally {
                isShizukuDumping.set(false)
            }
        }
    }

    private fun emitDebug(msg: String) {
        Log.d(TAG, msg)
        _debugLog.tryEmit(msg)
    }

    private fun extractChatInfoViaAccessibility() {
        val rootNode = rootInActiveWindow ?: return
        try {
            // Extract chat contact name from title bar
            val chatName = extractChatName(rootNode)
            if (chatName != null && chatName != currentChatName) {
                currentChatName = chatName
                lastMessages.clear()
                _currentFriendName.tryEmit(chatName)
            }

            // Extract messages with bounds
            val messagesWithBounds = collectMessagesWithBounds(rootNode)
            val messageTexts = messagesWithBounds.map { it.first }
            if (messageTexts.isNotEmpty() && messageTexts != lastMessages) {
                lastMessages = messageTexts.toMutableList()
                val chatMessages = parseToChatMessagesImproved(
                    messagesWithBounds, currentChatName ?: return
                )
                serviceScope.launch {
                    _newMessages.emit(chatMessages)
                }
            }
        } finally {
            @Suppress("DEPRECATION")
            rootNode.recycle()
        }
    }

    // ==================== Chat Name Extraction ====================

    private fun extractChatName(root: AccessibilityNodeInfo): String? {
        // Strategy 1: Known resource IDs for WeChat title
        val knownTitleIds = listOf(
            "$WECHAT_PACKAGE:id/action_bar_title_layout",
            "$WECHAT_PACKAGE:id/kgm",  // some versions
            "$WECHAT_PACKAGE:id/iwt",  // some versions
            "$WECHAT_PACKAGE:id/obn"   // some versions
        )
        for (titleId in knownTitleIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(titleId)
            if (nodes.isNotEmpty()) {
                val node = nodes[0]
                // The title layout may contain the name as a child TextView
                val name = extractTextFromNodeTree(node)
                @Suppress("DEPRECATION")
                node.recycle()
                if (name != null) return name
            }
        }

        // Strategy 2: Find the action bar directly
        val titleNodes = root.findAccessibilityNodeInfosByViewId("$WECHAT_PACKAGE:id/title_name")
        if (titleNodes.isNotEmpty()) {
            val text = titleNodes[0].text?.toString()
            @Suppress("DEPRECATION")
            titleNodes[0].recycle()
            if (!text.isNullOrBlank() && !isSystemText(text) && !isNonTitleText(text)) return text
        }

        // Strategy 3: Restricted top-area search
        return findTitleInTopArea(root)
    }

    /**
     * Extract the first valid text from a node or its children (shallow).
     */
    private fun extractTextFromNodeTree(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.length in 1..20
            && !isSystemText(text) && !isNonTitleText(text)
        ) {
            return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = child.text?.toString()
            @Suppress("DEPRECATION")
            child.recycle()
            if (!childText.isNullOrBlank() && childText.length in 1..20
                && !isSystemText(childText) && !isNonTitleText(childText)
            ) {
                return childText
            }
        }
        return null
    }

    private fun findTitleInTopArea(node: AccessibilityNodeInfo): String? {
        val screenHeight = resources.displayMetrics.heightPixels
        val topAreaLimit = (screenHeight * 0.12).toInt()  // Tighter: top 12%
        return findTitleInTopAreaRecursive(node, topAreaLimit)
    }

    private fun findTitleInTopAreaRecursive(node: AccessibilityNodeInfo, topAreaLimit: Int): String? {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // Only search in the top area
        if (rect.top > topAreaLimit) return null

        if (node.className?.toString() == "android.widget.TextView") {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && text.length in 1..20) {
                val resourceId = node.viewIdResourceName ?: ""

                // Exclude known non-title IDs
                val excludedIds = listOf(
                    "unread_count", "action_bar_subtitle", "tv_unread",
                    "action_bar_other", "chatting_status"
                )
                if (excludedIds.any { resourceId.contains(it) }) return null

                // Filter pure numbers
                if (text.matches(Regex("\\d+"))) return null

                // Filter system text and non-title text
                if (isSystemText(text)) return null
                if (isNonTitleText(text)) return null

                // Must be horizontally centered in the top bar (roughly)
                val screenWidth = resources.displayMetrics.widthPixels
                val centerX = (rect.left + rect.right) / 2
                if (centerX < screenWidth * 0.15 || centerX > screenWidth * 0.85) return null

                return text
            }
        }

        for (i in 0 until minOf(node.childCount, 8)) {
            val child = node.getChild(i) ?: continue
            val result = findTitleInTopAreaRecursive(child, topAreaLimit)
            @Suppress("DEPRECATION")
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /**
     * Texts that appear in the top bar area but are NOT the chat name.
     */
    private fun isNonTitleText(text: String): Boolean {
        // Button / UI labels that appear in top area
        val topBarLabels = listOf(
            "弹窗", "返回", "更多", "搜索", "聊天信息",
            "多选", "删除", "转发", "收藏", "引用",
            "对方正在输入...", "对方正在输入…"
        )
        if (topBarLabels.any { text == it }) return true

        // Parenthesized suffixes like "(3)" for group member count — strip them
        // But don't reject the whole thing — we just don't want pure matches like "(3)"
        if (text.matches(Regex("\\(\\d+\\)"))) return true

        return false
    }

    // ==================== Message Collection ====================

    private fun collectMessagesWithBounds(root: AccessibilityNodeInfo): List<Pair<String, Int?>> {
        val result = mutableListOf<Pair<String, Int?>>()

        // Find the message list view
        val listNodes = root.findAccessibilityNodeInfosByViewId("$WECHAT_PACKAGE:id/chatting_content_layout")
        val listNode = if (listNodes.isNotEmpty()) listNodes[0] else root

        collectMessagesWithBoundsRecursive(listNode, result)

        if (listNodes.isNotEmpty()) {
            @Suppress("DEPRECATION")
            listNode.recycle()
        }
        return result
    }

    private fun collectMessagesWithBoundsRecursive(
        node: AccessibilityNodeInfo,
        messages: MutableList<Pair<String, Int?>>
    ) {
        val text = node.text?.toString()
        val className = node.className?.toString() ?: ""

        if (className.contains("TextView") && !text.isNullOrBlank()) {
            if (text.length > 1 && !isSystemText(text) && !isNonMessageText(text)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val centerX = if (rect.width() > 0) (rect.left + rect.right) / 2 else null
                messages.add(Pair(text, centerX))
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMessagesWithBoundsRecursive(child, messages)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }

    // ==================== Text Filters ====================

    private fun isSystemText(text: String): Boolean {
        val exact = listOf(
            "发送", "按住", "说话", "更多", "表情", "返回", "微信",
            "语音通话", "视频通话", "以下是新消息", "查看更多消息",
            "+", "通讯录", "搜索", "聊天信息", "拍摄", "取消", "确定",
            "发现", "我", "文件传输助手", "对方正在输入...", "对方正在输入…"
        )
        return exact.any { text == it }
    }

    /**
     * Filter non-message content that appears inside the chat content area:
     * timestamps, recall notices, red packet text, system notices, etc.
     */
    private fun isNonMessageText(text: String): Boolean {
        // Time patterns: "3:45", "15:30", "下午 3:45", "上午 10:00"
        if (text.matches(Regex("\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("[上下]午\\s*\\d{1,2}:\\d{2}"))) return true

        // Date-time patterns: "昨天 下午 3:45", "星期一 10:00", "11月10日 3:45"
        if (text.matches(Regex("(昨天|前天|今天|星期[一二三四五六日天])\\s*[上下]?午?\\s*\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("\\d{1,2}月\\d{1,2}日\\s*[上下]?午?\\s*\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日\\s*[上下]?午?\\s*\\d{0,2}:?\\d{0,2}"))) return true

        // Recall messages
        if (text.contains("撤回了一条消息")) return true

        // Red packets
        if (text.matches(Regex(".*\\[微信红包].*"))) return true
        if (text == "微信红包" || text == "查看红包" || text == "领取红包") return true

        // Transfer
        if (text.matches(Regex(".*\\[转账].*"))) return true

        // System notices
        if (text.startsWith("你已添加了") || text.startsWith("以上是打招呼的内容")) return true
        if (text.contains("以下为新消息") || text.contains("以上为新消息")) return true
        if (text.startsWith("你通过") && text.contains("添加")) return true

        // Voice/video call records
        if (text == "通话时长" || text.matches(Regex("通话时长\\s*\\d+.*"))) return true
        if (text == "已取消" || text == "已拒绝" || text == "对方已取消" || text == "对方已拒绝") return true
        if (text == "未接听" || text == "对方未接听") return true

        // Emoji-only (single bracket notation)
        if (text.matches(Regex("^\\[.+]$")) && text.length <= 10) return true

        return false
    }

    // ==================== Message Parsing ====================

    private fun parseToChatMessagesImproved(
        messagesWithBounds: List<Pair<String, Int?>>,
        friendName: String
    ): List<ChatMessage> {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenCenter = screenWidth / 2

        val hasBoundsData = messagesWithBounds.any { it.second != null }

        return messagesWithBounds.mapIndexed { index, (content, centerX) ->
            val isVoice = content.startsWith("[语音] ")
            val actualContent = if (isVoice) content.removePrefix("[语音] ") else content

            val sender = when {
                hasBoundsData && centerX != null -> {
                    if (centerX > screenCenter) ChatMessage.SENDER_ME else ChatMessage.SENDER_FRIEND
                }
                else -> {
                    if (index == messagesWithBounds.size - 1) ChatMessage.SENDER_FRIEND
                    else ChatMessage.SENDER_ME
                }
            }

            ChatMessage(
                friendName = friendName,
                sender = sender,
                content = actualContent,
                messageType = if (isVoice) ChatMessage.TYPE_VOICE else ChatMessage.TYPE_TEXT,
                voiceTranscript = if (isVoice) actualContent else null,
                timestamp = System.currentTimeMillis() - (messagesWithBounds.size - index) * 1000L
            )
        }
    }

    // ==================== Lifecycle ====================

    override fun onInterrupt() {
        instance = null
        _serviceStatus.tryEmit(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        shizukuExtractor?.unbindService()
        shizukuExtractor?.unregisterListeners()
        serviceScope.cancel()
        _serviceStatus.tryEmit(false)
    }
}

package com.example.chatcoach.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import com.example.chatcoach.ChatCoachApp
import com.example.chatcoach.R
import com.example.chatcoach.data.db.entity.ChatMessage
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.network.LlmApiService
import com.example.chatcoach.network.models.MessageItem
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.cancellation.CancellationException

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var floatingView: View? = null
    private var collapsedView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false
    private var isPinned = false
    private var isDebugMode = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val llmService = LlmApiService()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentFriend: Friend? = null
    private var currentMessages: List<ChatMessage> = emptyList()
    private var suggestions: List<ReplyItem> = emptyList()
    private lateinit var themedContext: Context

    private var generateJob: Job? = null

    // Debug data
    private var debugFriendDetection: String = ""
    private var debugCapturedMessages: String = ""
    private var debugPrompt: String = ""
    private var debugRawResponse: String = ""
    private var debugShizukuLog: String = ""

    data class ReplyItem(val styleTag: String, val content: String)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        createFloatingWindow()
        observeMessages()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingWindow() {
        themedContext = ContextThemeWrapper(this, R.style.Theme_Chatcoach)
        floatingView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_window, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }

        collapsedView = floatingView?.findViewById(R.id.collapsed_view)
        expandedView = floatingView?.findViewById(R.id.expanded_view)

        expandedView?.visibility = View.GONE
        collapsedView?.visibility = View.VISIBLE

        // Listen for outside touches to collapse the window
        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && isExpanded && !isPinned) {
                toggleExpanded()
                true
            } else {
                false
            }
        }

        setupDragAndClick()
        setupExpandedView()

        windowManager.addView(floatingView, layoutParams)

        val prefs = ChatCoachApp.instance.preferences
        floatingView?.alpha = prefs.floatingWindowOpacity
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndClick() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        collapsedView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                    layoutParams.x = initialX - dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleExpanded()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupExpandedView() {
        floatingView?.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            toggleExpanded()
        }
        floatingView?.findViewById<View>(R.id.btn_regenerate)?.setOnClickListener {
            currentFriend?.let { friend ->
                generateSuggestions(friend, currentMessages)
            }
        }
        floatingView?.findViewById<View>(R.id.btn_pin)?.setOnClickListener {
            isPinned = !isPinned
            val iconRes = if (isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outline
            floatingView?.findViewById<ImageView>(R.id.btn_pin)?.setImageResource(iconRes)
        }
        floatingView?.findViewById<View>(R.id.btn_debug)?.setOnClickListener {
            isDebugMode = !isDebugMode
            floatingView?.findViewById<View>(R.id.debug_panel)?.visibility =
                if (isDebugMode) View.VISIBLE else View.GONE
            if (isDebugMode) updateDebugPanel()
        }
    }

    private fun toggleExpanded() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { toggleExpanded() }
            return
        }
        isExpanded = !isExpanded
        if (isExpanded) {
            collapsedView?.visibility = View.GONE
            expandedView?.visibility = View.VISIBLE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            expandedView?.visibility = View.GONE
            collapsedView?.visibility = View.VISIBLE
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun observeMessages() {
        serviceScope.launch {
            ChatAccessibilityService.isInChatPage.collect { inChat ->
                floatingView?.visibility = if (inChat) View.VISIBLE else View.GONE
            }
        }
        serviceScope.launch {
            ChatAccessibilityService.currentFriendName.collect { name ->
                val db = ChatCoachApp.instance.database
                currentFriend = db.friendDao().getFriendByName(name)
                floatingView?.findViewById<TextView>(R.id.tv_friend_name)?.text = name
                val statusText = if (currentFriend != null) "已匹配" else "未配置"
                floatingView?.findViewById<TextView>(R.id.tv_match_status)?.text = statusText

                debugFriendDetection = buildString {
                    appendLine("检测到名称: $name")
                    appendLine("匹配好友: ${currentFriend?.wechatName ?: "无"}")
                    appendLine("关系: ${currentFriend?.relationship ?: "N/A"}")
                }
                if (isDebugMode) updateDebugPanel()
            }
        }
        serviceScope.launch {
            ChatAccessibilityService.newMessages.collect { messages ->
                currentMessages = messages

                debugCapturedMessages = if (messages.isEmpty()) {
                    "(空)"
                } else {
                    messages.joinToString("\n") { "[${it.sender}] ${it.content}" }
                }
                if (isDebugMode) updateDebugPanel()

                val db = ChatCoachApp.instance.database
                db.chatMessageDao().insertAll(messages)

                val prefs = ChatCoachApp.instance.preferences
                if (prefs.isAutoTriggerEnabled && currentFriend != null) {
                    generateJob?.cancel()
                    generateJob = serviceScope.launch {
                        delay(500) // debounce
                        generateSuggestions(currentFriend!!, messages)
                    }
                }
            }
        }
        // Collect Shizuku debug logs
        serviceScope.launch {
            ChatAccessibilityService.debugLog.collect { msg ->
                debugShizukuLog = buildString {
                    if (debugShizukuLog.length > 1000) {
                        // Keep only last 1000 chars to avoid growing unbounded
                        append(debugShizukuLog.takeLast(800))
                        appendLine()
                    } else if (debugShizukuLog.isNotEmpty()) {
                        append(debugShizukuLog)
                        appendLine()
                    }
                    append(msg)
                }
                if (isDebugMode) updateDebugPanel()
            }
        }
    }

    private fun generateSuggestions(friend: Friend, messages: List<ChatMessage>) {
        generateJob?.cancel()
        generateJob = serviceScope.launch {
            showLoading(true)
            try {
                val db = ChatCoachApp.instance.database
                val prefs = ChatCoachApp.instance.preferences

                val config = if (friend.preferredModelId != null) {
                    db.llmConfigDao().getConfigById(friend.preferredModelId)
                } else {
                    db.llmConfigDao().getDefaultConfig()
                } ?: run {
                    showError("请先配置大模型")
                    return@launch
                }

                val allMessages = db.chatMessageDao().getRecentMessages(
                    friend.wechatName, prefs.maxContextMessages
                ).reversed()

                var summary: String? = null
                val contextMessages: List<ChatMessage>

                if (allMessages.size >= prefs.summaryThreshold) {
                    val splitPoint = allMessages.size - 20
                    val earlyMessages = allMessages.subList(0, splitPoint)
                    contextMessages = allMessages.subList(splitPoint, allMessages.size)

                    val summaryPrompt = PromptBuilder.buildSummaryPrompt(earlyMessages, friend.wechatName)
                    val summaryResponse = withContext(Dispatchers.IO) {
                        llmService.sendRequest(
                            config,
                            listOf(MessageItem.user(summaryPrompt))
                        )
                    }
                    summary = summaryResponse.choices?.firstOrNull()?.message?.content
                } else {
                    contextMessages = allMessages
                }

                val prompt = PromptBuilder.buildReplyPrompt(friend, contextMessages, summary)

                debugPrompt = prompt
                if (isDebugMode) updateDebugPanel()

                val response = withContext(Dispatchers.IO) {
                    llmService.sendRequest(
                        config,
                        listOf(MessageItem.system(prompt), MessageItem.user("请给出回复建议"))
                    )
                }

                val content = response.choices?.firstOrNull()?.message?.content ?: ""

                debugRawResponse = content.ifEmpty { "(空响应)" }
                if (isDebugMode) updateDebugPanel()

                suggestions = parseReplySuggestions(content)
                displaySuggestions(suggestions)

                response.usage?.let { usage ->
                    db.tokenUsageDao().insert(
                        com.example.chatcoach.data.db.entity.TokenUsage(
                            modelConfigId = config.id,
                            friendId = friend.id,
                            promptTokens = usage.promptTokens,
                            completionTokens = usage.completionTokens
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugRawResponse = buildString {
                    appendLine("ERROR: ${e.message}")
                    appendLine(e.stackTraceToString().take(500))
                }
                if (isDebugMode) updateDebugPanel()

                showError("生成失败: ${e.message?.take(50)}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun parseReplySuggestions(content: String): List<ReplyItem> {
        val regex = Regex("【(.+?)】(.+)")
        return content.lines()
            .mapNotNull { line ->
                val match = regex.find(line.trim())
                if (match != null) {
                    ReplyItem(
                        styleTag = match.groupValues[1],
                        content = match.groupValues[2].trim()
                    )
                } else null
            }
            .take(3)
    }

    private fun displaySuggestions(items: List<ReplyItem>) {
        val container = floatingView?.findViewById<LinearLayout>(R.id.suggestions_container) ?: return
        container.removeAllViews()

        items.forEach { item ->
            val cardView = LayoutInflater.from(themedContext)
                .inflate(R.layout.item_suggestion_card, container, false)
            cardView.findViewById<TextView>(R.id.tv_style_tag)?.text = item.styleTag
            cardView.findViewById<TextView>(R.id.tv_suggestion_content)?.text = item.content

            val tagColor = com.example.chatcoach.util.getStyleColor(item.styleTag)
            cardView.findViewById<TextView>(R.id.tv_style_tag)?.let { tv ->
                val drawable = tv.background?.mutate()
                drawable?.setTint(tagColor)
                tv.background = drawable
            }

            cardView.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ChatCoach", item.content))
                Toast.makeText(this, "已复制: ${item.styleTag}", Toast.LENGTH_SHORT).show()
            }

            container.addView(cardView)
        }

        if (!isExpanded) toggleExpanded()
    }

    private fun showLoading(loading: Boolean) {
        // Expanded state: LinearProgressIndicator — must use show()/hide()
        floatingView?.findViewById<LinearProgressIndicator>(R.id.progress_loading)?.let { indicator ->
            if (loading) indicator.show() else indicator.hide()
        }
        floatingView?.findViewById<View>(R.id.suggestions_container)?.visibility =
            if (loading) View.GONE else View.VISIBLE

        // Collapsed state: CircularProgressIndicator — must use show()/hide()
        floatingView?.findViewById<CircularProgressIndicator>(R.id.progress_collapsed)?.let { indicator ->
            if (loading) indicator.show() else indicator.hide()
        }
        floatingView?.findViewById<View>(R.id.iv_collapsed_icon)?.alpha =
            if (loading) 0.4f else 1.0f
    }

    private fun showError(message: String) {
        val container = floatingView?.findViewById<LinearLayout>(R.id.suggestions_container) ?: return
        container.removeAllViews()
        val tv = TextView(this).apply {
            text = message
            setTextColor(0xFFE53935.toInt())
            setPadding(16, 8, 16, 8)
        }
        container.addView(tv)
        if (!isExpanded) toggleExpanded()
    }

    private fun updateDebugPanel() {
        val prefs = ChatCoachApp.instance.preferences
        val modeLabel = if (prefs.isShizukuModeEnabled) "Shizuku" else "Accessibility"

        val text = buildString {
            appendLine("══ Mode: $modeLabel ══")
            appendLine()
            appendLine("══ Friend Detection ══")
            appendLine(debugFriendDetection.ifEmpty { getString(R.string.debug_no_data) })
            appendLine()
            appendLine("══ Captured Messages ══")
            appendLine(debugCapturedMessages.ifEmpty { getString(R.string.debug_no_data) })
            appendLine()
            appendLine("══ Prompt Sent ══")
            appendLine(debugPrompt.ifEmpty { getString(R.string.debug_no_data) })
            appendLine()
            appendLine("══ LLM Raw Response ══")
            appendLine(debugRawResponse.ifEmpty { getString(R.string.debug_no_data) })
            if (prefs.isShizukuModeEnabled) {
                appendLine()
                appendLine("══ Shizuku Log ══")
                appendLine(debugShizukuLog.ifEmpty { getString(R.string.debug_no_data) })
            }
        }
        floatingView?.findViewById<TextView>(R.id.tv_debug_content)?.text = text
    }

    fun updateOpacity(opacity: Float) {
        floatingView?.alpha = opacity
    }

    override fun onDestroy() {
        super.onDestroy()
        generateJob?.cancel()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
        serviceScope.cancel()
        instance = null
    }

    companion object {
        var instance: FloatingWindowService? = null
            private set
    }
}

package com.example.chatcoach.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.example.chatcoach.IShellService
import com.example.chatcoach.data.db.entity.ChatMessage
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import rikka.shizuku.Shizuku
import java.io.StringReader

class ShizukuChatExtractor {

    companion object {
        private const val TAG = "ShizukuExtractor"
        private const val SHIZUKU_REQUEST_CODE = 1001
    }

    private var shellService: IShellService? = null
    private var serviceBound = false
    private var screenWidth = 1080
    private var binderAlive = false
    private var permissionGranted = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            "com.example.chatcoach",
            ShellService::class.java.name
        )
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(true)
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShellService.Stub.asInterface(service)
            serviceBound = true
            Log.d(TAG, "ShellService connected")
            refreshScreenWidth()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            serviceBound = false
            Log.d(TAG, "ShellService disconnected")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        binderAlive = true
        checkPermissionAndBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        binderAlive = false
        permissionGranted = false
        shellService = null
        serviceBound = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                permissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Permission result: granted=$permissionGranted")
                if (permissionGranted) {
                    doBindService()
                }
            }
        }

    /**
     * Register Shizuku lifecycle listeners. Call this when enabling Shizuku mode.
     */
    fun registerListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            Log.d(TAG, "Shizuku listeners registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    /**
     * Unregister Shizuku lifecycle listeners. Call this when disabling Shizuku mode.
     */
    fun unregisterListeners() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            Log.d(TAG, "Shizuku listeners unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister Shizuku listeners", e)
        }
    }

    /**
     * Check if Shizuku binder is alive and permission is granted, then bind the shell service.
     */
    private fun checkPermissionAndBind() {
        if (!binderAlive) {
            Log.w(TAG, "checkPermissionAndBind: binder not alive")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                permissionGranted = true
                doBindService()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.w(TAG, "Shizuku permission denied permanently")
            } else {
                Log.d(TAG, "Requesting Shizuku permission")
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPermissionAndBind failed", e)
        }
    }

    fun bindService() {
        if (!isShizukuBinderAlive()) {
            Log.w(TAG, "bindService: Shizuku binder not alive, will bind when binder is received")
            return
        }
        checkPermissionAndBind()
    }

    private fun doBindService() {
        if (serviceBound) {
            Log.d(TAG, "doBindService: already bound")
            return
        }
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            Log.d(TAG, "bindUserService called")
        } catch (e: Exception) {
            Log.e(TAG, "bindService failed", e)
        }
    }

    fun unbindService() {
        try {
            if (serviceBound) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        } catch (_: Exception) {}
        shellService = null
        serviceBound = false
    }

    private fun isShizukuBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            binderAlive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun isAvailable(): Boolean {
        return serviceBound && shellService != null
    }

    fun getStatusText(): String {
        return when {
            isAvailable() -> "已连接 (screenWidth=$screenWidth)"
            isShizukuAvailable() -> "已授权，服务未连接"
            binderAlive -> "Shizuku 运行中，未授权"
            else -> "Shizuku 未运行"
        }
    }

    fun dumpUI(): String? {
        val service = shellService ?: return null
        return try {
            val dumpPath = "/data/local/tmp/chatcoach_ui.xml"
            val dumpResult = service.exec("uiautomator dump $dumpPath && chmod 666 $dumpPath")
            Log.d(TAG, "dump result: $dumpResult")
            val xml = service.exec("cat $dumpPath")
            if (xml.isNotEmpty() && xml.contains("<hierarchy")) xml else null
        } catch (e: Exception) {
            Log.e(TAG, "dumpUI failed", e)
            null
        }
    }

    fun extractChatName(xml: String): String? {
        val titleResourceIds = listOf(
            "com.tencent.mm:id/action_bar_title_layout",
            "com.tencent.mm:id/title_name",
            "com.tencent.mm:id/kgm",
            "com.tencent.mm:id/iwt",
            "com.tencent.mm:id/obn"
        )
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val resourceId = parser.getAttributeValue(null, "resource-id") ?: ""
                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val bounds = parser.getAttributeValue(null, "bounds") ?: ""

                    // Check if this is a title node by known resource IDs
                    if (text.isNotBlank() && titleResourceIds.any { resourceId.contains(it) }) {
                        if (!isSystemTextForXml(text) && !isNonTitleTextForXml(text)) {
                            return text
                        }
                    }

                    // Check for text node in top area with reasonable name length
                    if (text.isNotBlank() && text.length in 1..20 && bounds.isNotEmpty()) {
                        val top = parseBoundsTop(bounds)
                        if (top != null && top < screenWidth * 0.12) {
                            val className = parser.getAttributeValue(null, "class") ?: ""
                            if (className.contains("TextView")
                                && !isSystemTextForXml(text)
                                && !isNonTitleTextForXml(text)
                                && !text.matches(Regex("\\d+"))
                            ) {
                                // Check horizontal center — should be roughly centered
                                val center = parseBoundsCenter(bounds)
                                if (center != null && center > screenWidth * 0.15 && center < screenWidth * 0.85) {
                                    return text
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractChatName failed", e)
        }
        return null
    }

    fun extractMessages(xml: String, friendName: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val screenCenter = screenWidth / 2

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var inChatContent = false
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val resourceId = parser.getAttributeValue(null, "resource-id") ?: ""
                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val bounds = parser.getAttributeValue(null, "bounds") ?: ""
                    val className = parser.getAttributeValue(null, "class") ?: ""

                    if (resourceId.contains("chatting_content_layout")) {
                        inChatContent = true
                    }

                    if (inChatContent && className.contains("TextView")
                        && text.isNotBlank() && text.length > 1
                        && !isSystemTextForXml(text)
                        && !isNonMessageTextForXml(text)
                    ) {
                        val center = parseBoundsCenter(bounds)
                        val sender = if (center != null && center > screenCenter) {
                            ChatMessage.SENDER_ME
                        } else {
                            ChatMessage.SENDER_FRIEND
                        }

                        messages.add(
                            ChatMessage(
                                friendName = friendName,
                                sender = sender,
                                content = text,
                                messageType = ChatMessage.TYPE_TEXT,
                                timestamp = System.currentTimeMillis() - (messages.size) * 1000L
                            )
                        )
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractMessages failed", e)
        }
        return messages
    }

    fun parseBoundsCenter(bounds: String): Int? {
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(bounds) ?: return null
        val left = match.groupValues[1].toIntOrNull() ?: return null
        val right = match.groupValues[3].toIntOrNull() ?: return null
        return (left + right) / 2
    }

    private fun parseBoundsTop(bounds: String): Int? {
        val regex = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = regex.find(bounds) ?: return null
        return match.groupValues[2].toIntOrNull()
    }

    fun refreshScreenWidth() {
        val service = shellService ?: return
        try {
            val output = service.exec("wm size")
            val match = Regex("(\\d+)x(\\d+)").find(output)
            if (match != null) {
                screenWidth = match.groupValues[1].toInt()
                Log.d(TAG, "Screen width: $screenWidth")
            }
        } catch (_: Exception) {}
    }

    private fun isSystemTextForXml(text: String): Boolean {
        val systemPatterns = listOf(
            "发送", "按住", "说话", "更多", "表情", "返回", "微信",
            "语音通话", "视频通话", "以下是新消息", "查看更多消息",
            "+", "通讯录", "搜索", "聊天信息", "拍摄", "取消", "确定",
            "发现", "我", "文件传输助手", "对方正在输入...", "对方正在输入…"
        )
        return systemPatterns.any { text == it }
    }

    private fun isNonTitleTextForXml(text: String): Boolean {
        val labels = listOf(
            "弹窗", "返回", "更多", "搜索", "聊天信息",
            "多选", "删除", "转发", "收藏", "引用"
        )
        if (labels.any { text == it }) return true
        if (text.matches(Regex("\\(\\d+\\)"))) return true
        if (text.matches(Regex("\\d+"))) return true
        return false
    }

    /**
     * Filter non-message content in the XML chat area:
     * timestamps, recall notices, red packets, system notices, etc.
     */
    private fun isNonMessageTextForXml(text: String): Boolean {
        // Time patterns
        if (text.matches(Regex("\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("[上下]午\\s*\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("(昨天|前天|今天|星期[一二三四五六日天])\\s*[上下]?午?\\s*\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("\\d{1,2}月\\d{1,2}日\\s*[上下]?午?\\s*\\d{1,2}:\\d{2}"))) return true
        if (text.matches(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日\\s*[上下]?午?\\s*\\d{0,2}:?\\d{0,2}"))) return true

        // Recall messages
        if (text.contains("撤回了一条消息")) return true

        // Red packets & transfers
        if (text.matches(Regex(".*\\[微信红包].*"))) return true
        if (text == "微信红包" || text == "查看红包" || text == "领取红包") return true
        if (text.matches(Regex(".*\\[转账].*"))) return true

        // System notices
        if (text.startsWith("你已添加了") || text.startsWith("以上是打招呼的内容")) return true
        if (text.contains("以下为新消息") || text.contains("以上为新消息")) return true
        if (text.startsWith("你通过") && text.contains("添加")) return true

        // Voice/video call records
        if (text == "通话时长" || text.matches(Regex("通话时长\\s*\\d+.*"))) return true
        if (text == "已取消" || text == "已拒绝" || text == "对方已取消" || text == "对方已拒绝") return true
        if (text == "未接听" || text == "对方未接听") return true

        // Emoji-only
        if (text.matches(Regex("^\\[.+]$")) && text.length <= 10) return true

        return false
    }
}

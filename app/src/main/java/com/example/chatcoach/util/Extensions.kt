package com.example.chatcoach.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ChatCoach", text))
    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFullDateString(): String {
    val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

fun generateAvatarColor(): Int {
    val colors = intArrayOf(
        0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(), 0xFFEF5350.toInt(),
        0xFFAB47BC.toInt(), 0xFFFF7043.toInt(), 0xFF26A69A.toInt(),
        0xFFEC407A.toInt(), 0xFF7E57C2.toInt(), 0xFF5C6BC0.toInt(),
        0xFFFFCA28.toInt(), 0xFF8D6E63.toInt(), 0xFF78909C.toInt()
    )
    return colors.random()
}

val styleTagColors: Map<String, Int>
    get() = mapOf(
        "正式" to 0xFF1565C0.toInt(),
        "幽默" to 0xFFFF8F00.toInt(),
        "暖心" to 0xFFE91E63.toInt(),
        "简洁" to 0xFF00897B.toInt(),
        "俏皮" to 0xFFAB47BC.toInt(),
        "专业" to 0xFF37474F.toInt(),
        "轻松" to 0xFF43A047.toInt(),
        "亲切" to 0xFFEF6C00.toInt()
    )

fun getStyleColor(tag: String): Int {
    return styleTagColors[tag] ?: 0xFF757575.toInt()
}

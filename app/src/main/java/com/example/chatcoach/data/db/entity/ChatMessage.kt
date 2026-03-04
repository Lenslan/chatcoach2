package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val friendName: String,
    val sender: String,
    val content: String,
    val messageType: String = TYPE_TEXT,
    val voiceTranscript: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val SENDER_ME = "me"
        const val SENDER_FRIEND = "friend"
        const val TYPE_TEXT = "text"
        const val TYPE_VOICE = "voice"
    }

    fun getDisplayContent(): String {
        return if (messageType == TYPE_VOICE && !voiceTranscript.isNullOrBlank()) {
            voiceTranscript
        } else {
            content
        }
    }
}

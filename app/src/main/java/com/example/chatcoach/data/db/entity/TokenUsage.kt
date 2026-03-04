package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_usage")
data class TokenUsage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelConfigId: Long,
    val friendId: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalTokens: Int get() = promptTokens + completionTokens
}

package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_reviews")
data class ChatReview(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val friendId: Long,
    val startTime: Long,
    val endTime: Long,
    val messageCount: Int,
    val clarityScore: Int = 0,
    val toneScore: Int = 0,
    val emotionScore: Int = 0,
    val topicScore: Int = 0,
    val highlights: String = "[]",
    val improvements: String = "[]",
    val strategies: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
) {
    val averageScore: Float
        get() = (clarityScore + toneScore + emotionScore + topicScore) / 4f
}

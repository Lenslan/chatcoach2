package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_summaries")
data class ContextSummary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val friendName: String,
    val summary: String,
    val coveredMessageCount: Int,
    val lastMessageTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)

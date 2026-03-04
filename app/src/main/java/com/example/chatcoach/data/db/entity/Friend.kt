package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wechatName: String,
    val relationship: String = "",
    val tone: String = "",
    val attitude: String = "",
    val customPrompt: String? = null,
    val notes: String? = null,
    val preferredModelId: Long? = null,
    val avatarColor: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

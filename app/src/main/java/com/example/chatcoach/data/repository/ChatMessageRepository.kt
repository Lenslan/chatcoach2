package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.ChatMessageDao
import com.example.chatcoach.data.db.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

class ChatMessageRepository(private val dao: ChatMessageDao) {

    suspend fun getRecentMessages(friendName: String, limit: Int = 20): List<ChatMessage> =
        dao.getRecentMessages(friendName, limit)

    fun getMessagesByFriend(friendName: String): Flow<List<ChatMessage>> =
        dao.getMessagesByFriend(friendName)

    suspend fun getMessagesInRange(friendName: String, start: Long, end: Long): List<ChatMessage> =
        dao.getMessagesInRange(friendName, start, end)

    suspend fun addMessage(message: ChatMessage): Long = dao.insert(message)

    suspend fun addMessages(messages: List<ChatMessage>) = dao.insertAll(messages)

    suspend fun cleanOldMessages(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        dao.deleteOlderThan(cutoff)
    }

    suspend fun deleteByFriend(friendName: String) = dao.deleteByFriend(friendName)

    suspend fun getCountByFriend(friendName: String): Int = dao.getCountByFriend(friendName)

    fun getActiveFriendNames(): Flow<List<String>> = dao.getActiveFriendNames()
}

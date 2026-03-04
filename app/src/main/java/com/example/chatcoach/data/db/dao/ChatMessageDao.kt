package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE friendName = :friendName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(friendName: String, limit: Int = 20): List<ChatMessage>

    @Query("SELECT * FROM chat_messages WHERE friendName = :friendName ORDER BY timestamp ASC")
    fun getMessagesByFriend(friendName: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE friendName = :friendName AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getMessagesInRange(friendName: String, start: Long, end: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>)

    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM chat_messages WHERE friendName = :friendName")
    suspend fun deleteByFriend(friendName: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE friendName = :friendName")
    suspend fun getCountByFriend(friendName: String): Int

    @Query("SELECT DISTINCT friendName FROM chat_messages ORDER BY timestamp DESC")
    fun getActiveFriendNames(): Flow<List<String>>
}

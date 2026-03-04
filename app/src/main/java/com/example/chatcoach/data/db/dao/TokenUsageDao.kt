package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.TokenUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenUsageDao {
    @Query("SELECT * FROM token_usage ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUsage(limit: Int = 50): Flow<List<TokenUsage>>

    @Query("SELECT SUM(promptTokens + completionTokens) FROM token_usage")
    suspend fun getTotalTokens(): Long?

    @Query("SELECT SUM(promptTokens + completionTokens) FROM token_usage WHERE modelConfigId = :modelId")
    suspend fun getTotalTokensByModel(modelId: Long): Long?

    @Query("SELECT SUM(promptTokens + completionTokens) FROM token_usage WHERE friendId = :friendId")
    suspend fun getTotalTokensByFriend(friendId: Long): Long?

    @Query("SELECT SUM(promptTokens + completionTokens) FROM token_usage WHERE timestamp > :since")
    suspend fun getTokensSince(since: Long): Long?

    @Insert
    suspend fun insert(usage: TokenUsage): Long

    @Query("DELETE FROM token_usage WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.TokenUsageDao
import com.example.chatcoach.data.db.entity.TokenUsage
import kotlinx.coroutines.flow.Flow

class TokenUsageRepository(private val dao: TokenUsageDao) {

    fun getRecentUsage(limit: Int = 50): Flow<List<TokenUsage>> = dao.getRecentUsage(limit)

    suspend fun getTotalTokens(): Long = dao.getTotalTokens() ?: 0

    suspend fun getTotalTokensByModel(modelId: Long): Long = dao.getTotalTokensByModel(modelId) ?: 0

    suspend fun getTotalTokensByFriend(friendId: Long): Long = dao.getTotalTokensByFriend(friendId) ?: 0

    suspend fun getTodayTokens(): Long {
        val startOfDay = System.currentTimeMillis().let { now ->
            now - now % (24 * 60 * 60 * 1000)
        }
        return dao.getTokensSince(startOfDay) ?: 0
    }

    suspend fun addUsage(usage: TokenUsage): Long = dao.insert(usage)
}

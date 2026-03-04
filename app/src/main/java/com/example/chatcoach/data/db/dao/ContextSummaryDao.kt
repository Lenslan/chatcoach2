package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.ContextSummary

@Dao
interface ContextSummaryDao {
    @Query("SELECT * FROM context_summaries WHERE friendName = :friendName ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSummary(friendName: String): ContextSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: ContextSummary): Long

    @Query("DELETE FROM context_summaries WHERE friendName = :friendName")
    suspend fun deleteByFriend(friendName: String)

    @Query("DELETE FROM context_summaries WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.ChatReview
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatReviewDao {
    @Query("SELECT * FROM chat_reviews WHERE friendId = :friendId ORDER BY createdAt DESC")
    fun getReviewsByFriend(friendId: Long): Flow<List<ChatReview>>

    @Query("SELECT * FROM chat_reviews WHERE id = :id")
    suspend fun getById(id: Long): ChatReview?

    @Query("SELECT * FROM chat_reviews ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentReviews(limit: Int = 20): Flow<List<ChatReview>>

    @Insert
    suspend fun insert(review: ChatReview): Long

    @Delete
    suspend fun delete(review: ChatReview)
}

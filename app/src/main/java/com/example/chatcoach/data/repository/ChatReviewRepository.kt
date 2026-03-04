package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.ChatReviewDao
import com.example.chatcoach.data.db.entity.ChatReview
import kotlinx.coroutines.flow.Flow

class ChatReviewRepository(private val dao: ChatReviewDao) {

    fun getReviewsByFriend(friendId: Long): Flow<List<ChatReview>> = dao.getReviewsByFriend(friendId)

    suspend fun getById(id: Long): ChatReview? = dao.getById(id)

    fun getRecentReviews(limit: Int = 20): Flow<List<ChatReview>> = dao.getRecentReviews(limit)

    suspend fun addReview(review: ChatReview): Long = dao.insert(review)

    suspend fun deleteReview(review: ChatReview) = dao.delete(review)
}

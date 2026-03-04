package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.FriendDao
import com.example.chatcoach.data.db.entity.Friend
import com.example.chatcoach.util.generateAvatarColor
import kotlinx.coroutines.flow.Flow

class FriendRepository(private val friendDao: FriendDao) {

    fun getAllFriends(): Flow<List<Friend>> = friendDao.getAllFriends()

    fun searchFriends(query: String): Flow<List<Friend>> = friendDao.searchFriends(query)

    suspend fun getFriendById(id: Long): Friend? = friendDao.getFriendById(id)

    suspend fun getFriendByName(name: String): Friend? = friendDao.getFriendByName(name)

    suspend fun addFriend(friend: Friend): Long {
        val withColor = if (friend.avatarColor == 0) {
            friend.copy(avatarColor = generateAvatarColor())
        } else friend
        return friendDao.insert(withColor)
    }

    suspend fun updateFriend(friend: Friend) {
        friendDao.update(friend.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteFriend(friend: Friend) = friendDao.delete(friend)

    suspend fun getCount(): Int = friendDao.getCount()
}

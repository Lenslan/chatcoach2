package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY updatedAt DESC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun getFriendById(id: Long): Friend?

    @Query("SELECT * FROM friends WHERE wechatName = :name LIMIT 1")
    suspend fun getFriendByName(name: String): Friend?

    @Query("SELECT * FROM friends WHERE wechatName LIKE '%' || :query || '%'")
    fun searchFriends(query: String): Flow<List<Friend>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(friend: Friend): Long

    @Update
    suspend fun update(friend: Friend)

    @Delete
    suspend fun delete(friend: Friend)

    @Query("SELECT COUNT(*) FROM friends")
    suspend fun getCount(): Int
}

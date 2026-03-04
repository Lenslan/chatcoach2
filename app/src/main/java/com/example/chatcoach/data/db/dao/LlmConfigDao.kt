package com.example.chatcoach.data.db.dao

import androidx.room.*
import com.example.chatcoach.data.db.entity.LlmConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface LlmConfigDao {
    @Query("SELECT * FROM llm_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<LlmConfig>>

    @Query("SELECT * FROM llm_configs WHERE id = :id")
    suspend fun getConfigById(id: Long): LlmConfig?

    @Query("SELECT * FROM llm_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfig(): LlmConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: LlmConfig): Long

    @Update
    suspend fun update(config: LlmConfig)

    @Delete
    suspend fun delete(config: LlmConfig)

    @Query("UPDATE llm_configs SET isDefault = 0")
    suspend fun clearDefault()

    @Transaction
    suspend fun setDefault(id: Long) {
        clearDefault()
        getConfigById(id)?.let { update(it.copy(isDefault = true)) }
    }
}

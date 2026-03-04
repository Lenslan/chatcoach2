package com.example.chatcoach.data.repository

import com.example.chatcoach.data.db.dao.LlmConfigDao
import com.example.chatcoach.data.db.entity.LlmConfig
import kotlinx.coroutines.flow.Flow

class LlmConfigRepository(private val llmConfigDao: LlmConfigDao) {

    fun getAllConfigs(): Flow<List<LlmConfig>> = llmConfigDao.getAllConfigs()

    suspend fun getConfigById(id: Long): LlmConfig? = llmConfigDao.getConfigById(id)

    suspend fun getDefaultConfig(): LlmConfig? = llmConfigDao.getDefaultConfig()

    suspend fun addConfig(config: LlmConfig): Long = llmConfigDao.insert(config)

    suspend fun updateConfig(config: LlmConfig) = llmConfigDao.update(config)

    suspend fun deleteConfig(config: LlmConfig) = llmConfigDao.delete(config)

    suspend fun setDefault(id: Long) = llmConfigDao.setDefault(id)
}

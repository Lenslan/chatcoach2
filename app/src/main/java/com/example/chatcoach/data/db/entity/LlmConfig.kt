package com.example.chatcoach.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "llm_configs")
data class LlmConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val platform: String,
    val apiUrl: String,
    val apiKey: String,
    val modelName: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PLATFORM_OPENAI = "openai"
        const val PLATFORM_CLAUDE = "claude"
        const val PLATFORM_DEEPSEEK = "deepseek"
        const val PLATFORM_GEMINI = "gemini"
        const val PLATFORM_QWEN = "qwen"
        const val PLATFORM_GROK = "grok"
        const val PLATFORM_GLM = "glm"
        const val PLATFORM_OLLAMA = "ollama"
        const val PLATFORM_CUSTOM = "custom"
    }
}

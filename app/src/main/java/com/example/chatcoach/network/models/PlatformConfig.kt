package com.example.chatcoach.network.models

import com.example.chatcoach.data.db.entity.LlmConfig

data class PlatformConfig(
    val name: String,
    val platform: String,
    val defaultUrl: String,
    val defaultModels: List<String>,
    val description: String
) {
    companion object {
        fun getAllPlatforms(): List<PlatformConfig> = listOf(
            PlatformConfig(
                name = "OpenAI",
                platform = LlmConfig.PLATFORM_OPENAI,
                defaultUrl = "https://api.openai.com/v1",
                defaultModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
                description = "ChatGPT 系列模型"
            ),
            PlatformConfig(
                name = "Claude",
                platform = LlmConfig.PLATFORM_CLAUDE,
                defaultUrl = "https://api.anthropic.com/v1",
                defaultModels = listOf("claude-sonnet-4-20250514", "claude-3-5-haiku-20241022"),
                description = "Anthropic Claude 系列"
            ),
            PlatformConfig(
                name = "DeepSeek",
                platform = LlmConfig.PLATFORM_DEEPSEEK,
                defaultUrl = "https://api.deepseek.com",
                defaultModels = listOf("deepseek-chat", "deepseek-reasoner"),
                description = "DeepSeek 系列模型"
            ),
            PlatformConfig(
                name = "Gemini",
                platform = LlmConfig.PLATFORM_GEMINI,
                defaultUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                defaultModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro"),
                description = "Google Gemini 系列"
            ),
            PlatformConfig(
                name = "通义千问",
                platform = LlmConfig.PLATFORM_QWEN,
                defaultUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                defaultModels = listOf("qwen-turbo", "qwen-plus", "qwen-max"),
                description = "阿里云通义千问系列"
            ),
            PlatformConfig(
                name = "Grok",
                platform = LlmConfig.PLATFORM_GROK,
                defaultUrl = "https://api.x.ai/v1",
                defaultModels = listOf("grok-3", "grok-3-mini", "grok-2"),
                description = "xAI Grok 系列模型"
            ),
            PlatformConfig(
                name = "智谱GLM",
                platform = LlmConfig.PLATFORM_GLM,
                defaultUrl = "https://open.bigmodel.cn/api/paas/v4",
                defaultModels = listOf("glm-4-plus", "glm-4-flash", "glm-4-air"),
                description = "智谱AI GLM 系列模型"
            ),
            PlatformConfig(
                name = "Ollama",
                platform = LlmConfig.PLATFORM_OLLAMA,
                defaultUrl = "http://localhost:11434/v1",
                defaultModels = listOf("llama3", "qwen2", "mistral", "gemma"),
                description = "本地部署模型"
            ),
            PlatformConfig(
                name = "自定义",
                platform = LlmConfig.PLATFORM_CUSTOM,
                defaultUrl = "",
                defaultModels = emptyList(),
                description = "兼容 OpenAI 格式的自定义接口"
            )
        )

        fun getPlatformByKey(key: String): PlatformConfig? {
            return getAllPlatforms().find { it.platform == key }
        }
    }
}

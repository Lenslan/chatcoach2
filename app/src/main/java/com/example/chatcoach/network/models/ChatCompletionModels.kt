package com.example.chatcoach.network.models

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageItem>,
    val stream: Boolean = false,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val temperature: Double = 0.8
)

data class MessageItem(
    val role: String,
    val content: String
) {
    companion object {
        fun system(content: String) = MessageItem("system", content)
        fun user(content: String) = MessageItem("user", content)
        fun assistant(content: String) = MessageItem("assistant", content)
    }
}

data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null,
    val error: ErrorInfo? = null
)

data class Choice(
    val index: Int = 0,
    val message: MessageItem? = null,
    val delta: Delta? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class Delta(
    val role: String? = null,
    val content: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)

data class ErrorInfo(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

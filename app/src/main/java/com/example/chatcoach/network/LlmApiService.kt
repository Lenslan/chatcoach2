package com.example.chatcoach.network

import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.network.models.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LlmApiService {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendRequest(
        config: LlmConfig,
        messages: List<MessageItem>,
        stream: Boolean = false
    ): ChatCompletionResponse {
        if (config.platform == LlmConfig.PLATFORM_CLAUDE) {
            return sendClaudeRequest(config, messages)
        }
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = messages,
            stream = false
        )
        val jsonBody = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(config.apiUrl)
            .addHeader("Content-Type", "application/json")
            .apply { addAuthHeader(this, config) }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string()
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("API 请求失败 (${response.code}): ${body?.take(200)}")
                            )
                            return
                        }
                        val result = gson.fromJson(body, ChatCompletionResponse::class.java)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private suspend fun sendClaudeRequest(
        config: LlmConfig,
        messages: List<MessageItem>
    ): ChatCompletionResponse {
        val systemMessage = messages.firstOrNull { it.role == "system" }
        val otherMessages = messages.filter { it.role != "system" }

        val claudeBody = buildMap {
            put("model", config.modelName)
            put("max_tokens", 1024)
            put("messages", otherMessages.map { mapOf("role" to it.role, "content" to it.content) })
            if (systemMessage != null) {
                put("system", systemMessage.content)
            }
        }
        val jsonBody = gson.toJson(claudeBody)
        val httpRequest = Request.Builder()
            .url(config.apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string()
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("Claude API 请求失败 (${response.code}): ${body?.take(200)}")
                            )
                            return
                        }
                        val json = gson.fromJson(body, Map::class.java)
                        @Suppress("UNCHECKED_CAST")
                        val content = (json["content"] as? List<Map<String, Any>>)
                            ?.firstOrNull()?.get("text") as? String ?: ""
                        val usageMap = json["usage"] as? Map<String, Any>
                        val usage = Usage(
                            promptTokens = (usageMap?.get("input_tokens") as? Double)?.toInt() ?: 0,
                            completionTokens = (usageMap?.get("output_tokens") as? Double)?.toInt() ?: 0
                        )
                        val result = ChatCompletionResponse(
                            choices = listOf(Choice(message = MessageItem.assistant(content))),
                            usage = usage
                        )
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    fun streamRequest(
        config: LlmConfig,
        messages: List<MessageItem>
    ): Flow<String> = flow {
        val request = ChatCompletionRequest(
            model = config.modelName,
            messages = messages,
            stream = true
        )
        val jsonBody = gson.toJson(request)
        val httpRequest = Request.Builder()
            .url(config.apiUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .apply { addAuthHeader(this, config) }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = suspendCancellableCoroutine<Response> { cont ->
            val call = client.newCall(httpRequest)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }

        if (!response.isSuccessful) {
            throw IOException("Stream API 请求失败 (${response.code})")
        }

        val source = response.body?.source() ?: throw IOException("Empty response body")
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, ChatCompletionResponse::class.java)
                        val content = chunk.choices?.firstOrNull()?.delta?.content
                        if (!content.isNullOrEmpty()) {
                            emit(content)
                        }
                    } catch (_: Exception) { }
                }
            }
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(config: LlmConfig): Result<String> {
        return try {
            val messages = listOf(
                MessageItem.system("你好"),
                MessageItem.user("请回复'连接成功'四个字")
            )
            val response = sendRequest(config, messages)
            val content = response.choices?.firstOrNull()?.message?.content ?: "无回复"
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addAuthHeader(builder: Request.Builder, config: LlmConfig) {
        when (config.platform) {
            LlmConfig.PLATFORM_CLAUDE -> {
                builder.addHeader("x-api-key", config.apiKey)
                builder.addHeader("anthropic-version", "2023-06-01")
            }
            LlmConfig.PLATFORM_GEMINI -> {
                builder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }
            LlmConfig.PLATFORM_OLLAMA -> { /* no auth needed */ }
            else -> {
                builder.addHeader("Authorization", "Bearer ${config.apiKey}")
            }
        }
    }
}

package com.example.chatcoach.network

import com.example.chatcoach.data.db.entity.LlmConfig
import com.example.chatcoach.network.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiDebugInfo(
    val url: String,
    val statusCode: Int,
    val headers: String,
    val body: String,
    val timestamp: String
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("url", url)
        json.put("statusCode", statusCode)
        json.put("headers", headers)
        json.put("body", body)
        json.put("timestamp", timestamp)
        return json.toString(2)
    }
}

class LlmApiService {

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var lastDebugInfo: ApiDebugInfo? = null
        private set
    var lastRawResponseBody: String? = null
        private set

    suspend fun sendRequest(
        config: LlmConfig,
        messages: List<MessageItem>,
        stream: Boolean = false
    ): ChatCompletionResponse {
        if (config.platform == LlmConfig.PLATFORM_CLAUDE) {
            return sendClaudeRequest(config, messages)
        }

        val messagesArray = JSONArray()
        for (msg in messages) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", config.modelName)
            put("messages", messagesArray)
            put("max_tokens", 1024)
            put("temperature", 0.8)
        }

        val requestUrl = buildChatCompletionsUrl(config.apiUrl)
        val jsonBody = requestBody.toString()
        val httpRequest = Request.Builder()
            .url(requestUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .apply { addAuthHeader(this, config) }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return executeWithRetry { attemptRequest(httpRequest, config) }
    }

    private suspend fun attemptRequest(
        httpRequest: Request,
        config: LlmConfig
    ): ChatCompletionResponse {
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
                        lastRawResponseBody = body
                        lastDebugInfo = buildDebugInfo(
                            url = httpRequest.url.toString(),
                            statusCode = response.code,
                            headers = response.headers.toString(),
                            body = body ?: ""
                        )
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(
                                IOException("API 请求失败 (${response.code}): ${body?.take(200)}")
                            )
                            return
                        }
                        val json = JSONObject(extractJsonBody(body ?: "{}"))
                        val result = parseOpenAIResponse(json)
                        continuation.resume(result)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun parseOpenAIResponse(json: JSONObject): ChatCompletionResponse {
        val choicesArray = json.optJSONArray("choices")
        val choices = mutableListOf<Choice>()
        if (choicesArray != null) {
            for (i in 0 until choicesArray.length()) {
                val choiceObj = choicesArray.getJSONObject(i)
                val messageObj = choiceObj.optJSONObject("message")
                val deltaObj = choiceObj.optJSONObject("delta")
                choices.add(
                    Choice(
                        index = choiceObj.optInt("index", 0),
                        message = if (messageObj != null) MessageItem(
                            role = messageObj.optString("role", "assistant"),
                            content = extractContentFromMessage(messageObj)
                        ) else null,
                        delta = if (deltaObj != null) Delta(
                            role = optStringOrNull(deltaObj, "role"),
                            content = optStringOrNull(deltaObj, "content")
                        ) else null,
                        finishReason = optStringOrNull(choiceObj, "finish_reason")
                    )
                )
            }
        }

        val usageObj = json.optJSONObject("usage")
        val usage = if (usageObj != null) Usage(
            promptTokens = usageObj.optInt("prompt_tokens", 0),
            completionTokens = usageObj.optInt("completion_tokens", 0),
            totalTokens = usageObj.optInt("total_tokens", 0)
        ) else null

        return ChatCompletionResponse(
            id = optStringOrNull(json, "id"),
            choices = choices,
            usage = usage
        )
    }

    /**
     * Safely get a string value from JSONObject, returning null for missing keys or JSON null.
     * Unlike getString() which throws on null, and optString() which returns "null" string.
     */
    private fun optStringOrNull(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key)
    }

    /**
     * Extract content from a message object, handling various provider quirks:
     * - Standard: content field as string
     * - DeepSeek reasoner: content may be null/empty, use reasoning_content as fallback
     * - Some providers: content may be JSON null
     */
    private fun extractContentFromMessage(messageObj: JSONObject): String {
        // Try standard content field first
        val content = if (messageObj.has("content") && !messageObj.isNull("content")) {
            messageObj.optString("content", "")
        } else {
            ""
        }
        if (content.isNotEmpty()) return content

        // Fallback: reasoning_content (DeepSeek reasoner)
        if (messageObj.has("reasoning_content") && !messageObj.isNull("reasoning_content")) {
            val reasoning = messageObj.optString("reasoning_content", "")
            if (reasoning.isNotEmpty()) return reasoning
        }

        return ""
    }

    private suspend fun sendClaudeRequest(
        config: LlmConfig,
        messages: List<MessageItem>
    ): ChatCompletionResponse {
        val systemMessage = messages.firstOrNull { it.role == "system" }
        val otherMessages = messages.filter { it.role != "system" }

        val messagesArray = JSONArray()
        for (msg in otherMessages) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", config.modelName)
            put("max_tokens", 1024)
            put("messages", messagesArray)
            if (systemMessage != null) {
                put("system", systemMessage.content)
            }
        }

        val requestUrl = buildClaudeMessagesUrl(config.apiUrl)
        val jsonBody = requestBody.toString()
        val httpRequest = Request.Builder()
            .url(requestUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return executeWithRetry {
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(httpRequest)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val body = response.body?.string()
                            lastRawResponseBody = body
                            lastDebugInfo = buildDebugInfo(
                                url = httpRequest.url.toString(),
                                statusCode = response.code,
                                headers = response.headers.toString(),
                                body = body ?: ""
                            )
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("Claude API 请求失败 (${response.code}): ${body?.take(200)}")
                                )
                                return
                            }
                            val json = JSONObject(body ?: "{}")
                            val contentArray = json.optJSONArray("content")
                            val content = if (contentArray != null && contentArray.length() > 0) {
                                contentArray.getJSONObject(0).optString("text", "")
                            } else ""

                            val usageObj = json.optJSONObject("usage")
                            val usage = Usage(
                                promptTokens = usageObj?.optInt("input_tokens", 0) ?: 0,
                                completionTokens = usageObj?.optInt("output_tokens", 0) ?: 0
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
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                return block()
            } catch (e: UnknownHostException) {
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: SocketTimeoutException) {
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: IOException) {
                if (e.message?.contains("API 请求失败") == true ||
                    e.message?.contains("Claude API 请求失败") == true
                ) {
                    throw e
                }
                lastException = e
                if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
            } catch (e: Exception) {
                throw e
            }
        }
        throw lastException ?: IOException("Unknown error after $MAX_RETRIES retries")
    }

    fun streamRequest(
        config: LlmConfig,
        messages: List<MessageItem>
    ): Flow<String> = flow {
        val messagesArray = JSONArray()
        for (msg in messages) {
            messagesArray.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", config.modelName)
            put("messages", messagesArray)
            put("max_tokens", 1024)
            put("temperature", 0.8)
            put("stream", true)
        }

        val jsonBody = requestBody.toString()
        val requestUrl = buildChatCompletionsUrl(config.apiUrl)
        val httpRequest = Request.Builder()
            .url(requestUrl)
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
                        val chunk = JSONObject(data)
                        val choicesArray = chunk.optJSONArray("choices")
                        if (choicesArray != null && choicesArray.length() > 0) {
                            val delta = choicesArray.getJSONObject(0).optJSONObject("delta")
                            val content = if (delta != null && delta.has("content")) delta.getString("content") else null
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                            }
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

    suspend fun fetchModels(
        apiUrl: String,
        apiKey: String,
        platform: String
    ): Pair<Result<List<String>>, ApiDebugInfo?> = withContext(Dispatchers.IO) {
        if (apiUrl.isBlank()) {
            return@withContext Pair(Result.failure(Exception("API 地址不能为空")), null)
        }

        val fetchClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val cleanBaseUrl = normalizeBaseUrl(apiUrl)

        val request = try {
            Request.Builder()
                .url("$cleanBaseUrl/models")
                .apply { addAuthHeaderForFetch(this, apiKey, platform) }
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext Pair(
                Result.failure(Exception("API 地址格式无效: ${e.message}")),
                null
            )
        }

        try {
            fetchClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val debugInfo = buildDebugInfo(
                    url = request.url.toString(),
                    statusCode = response.code,
                    headers = response.headers.toString(),
                    body = responseBody
                )
                lastDebugInfo = debugInfo

                if (response.isSuccessful) {
                    val json = JSONObject(extractJsonBody(responseBody))
                    val data = json.optJSONArray("data")
                    val models = mutableListOf<String>()
                    if (data != null) {
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i)
                            val id = item?.optString("id", "")?.trim() ?: ""
                            if (id.isNotEmpty()) {
                                models.add(id)
                            }
                        }
                    }
                    Pair(Result.success(models), debugInfo)
                } else {
                    Pair(
                        Result.failure(Exception("HTTP ${response.code}: ${responseBody.take(200)}")),
                        debugInfo
                    )
                }
            }
        } catch (e: Exception) {
            val debugInfo = buildDebugInfo(
                url = request.url.toString(),
                statusCode = -1,
                headers = "",
                body = e.message ?: e.toString()
            )
            lastDebugInfo = debugInfo
            Pair(Result.failure(e), debugInfo)
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        var normalized = url.trim().removeSuffix("/")
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        normalized = normalized.removeSuffix("/chat/completions")
        normalized = normalized.removeSuffix("/messages")
        return normalized
    }

    /**
     * Extract JSON from a response body that may be in SSE format.
     * Some APIs (e.g., xAI Grok) may return SSE-formatted responses ("data: {...}")
     * even for non-streaming requests. This method handles both plain JSON and SSE formats.
     */
    private fun extractJsonBody(body: String): String {
        val trimmed = body.trim()
        // Already valid JSON object
        if (trimmed.startsWith("{")) return trimmed
        // SSE format: extract JSON from "data: {...}" lines
        if (trimmed.startsWith("data:") || trimmed.startsWith("data ")) {
            for (line in trimmed.lines()) {
                val stripped = line.removePrefix("data:").removePrefix("data ").trim()
                if (stripped.startsWith("{") && stripped != "[DONE]") {
                    return stripped
                }
            }
        }
        // Fallback: return as-is and let JSONObject throw a descriptive error
        return trimmed.ifEmpty { "{}" }
    }

    /**
     * Build the full chat completions URL from a base URL or full URL.
     * Handles both cases:
     * - Base URL: "https://api.openai.com/v1" → "https://api.openai.com/v1/chat/completions"
     * - Full URL: "https://api.openai.com/v1/chat/completions" → unchanged
     */
    private fun buildChatCompletionsUrl(apiUrl: String): String {
        val base = normalizeBaseUrl(apiUrl)
        return "$base/chat/completions"
    }

    /**
     * Build the full Claude messages URL from a base URL or full URL.
     * Handles both cases:
     * - Base URL: "https://api.anthropic.com/v1" → "https://api.anthropic.com/v1/messages"
     * - Full URL: "https://api.anthropic.com/v1/messages" → unchanged
     */
    private fun buildClaudeMessagesUrl(apiUrl: String): String {
        val base = normalizeBaseUrl(apiUrl)
        return "$base/messages"
    }

    private fun addAuthHeaderForFetch(builder: Request.Builder, apiKey: String, platform: String) {
        when (platform) {
            LlmConfig.PLATFORM_CLAUDE -> {
                builder.addHeader("x-api-key", apiKey)
                builder.addHeader("anthropic-version", "2023-06-01")
            }
            LlmConfig.PLATFORM_OLLAMA -> { /* no auth needed */ }
            else -> {
                if (apiKey.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $apiKey")
                }
            }
        }
    }

    private fun buildDebugInfo(url: String, statusCode: Int, headers: String, body: String): ApiDebugInfo {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return ApiDebugInfo(
            url = url,
            statusCode = statusCode,
            headers = headers,
            body = body.take(2000),
            timestamp = sdf.format(Date())
        )
    }
}

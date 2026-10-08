package com.copilotenhanced.app.llm

import com.copilotenhanced.app.settings.ProviderConfig
import com.copilotenhanced.app.settings.ProviderConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class OpenAiCompatibleClient(
    private val configStore: ProviderConfigStore? = null,
    private val customConfig: ProviderConfig? = null,
) : LlmClient {

    constructor(config: ProviderConfig) : this(null, config)
    constructor(configStore: ProviderConfigStore) : this(configStore, null)

    private fun resolveConfig(): ProviderConfig {
        return configStore?.load() ?: customConfig ?: ProviderConfig()
    }

    override fun stream(messages: List<ChatMessage>, system: String?, tools: List<ToolDef>?): Flow<LlmEvent> = flow {
        val config = resolveConfig()
        val endpointUrl = normalizeChatCompletionsUrl(config.baseUrl)

        if (endpointUrl.isBlank()) {
            emit(LlmEvent.Error("Base URL is empty or unconfigured. Please configure Base URL in settings."))
            return@flow
        }

        // Log before each request (never the key): full URL, model name, and key length
        println("[OpenAiCompatibleClient] Requesting URL: $endpointUrl, model: ${config.modelName}, keyLength: ${config.apiKey.length}")

        val payload = buildRequestPayload(config, messages, system, tools)
        val requestBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)

        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            }
        }

        try {
            connection.outputStream.use { os ->
                os.write(requestBytes)
                os.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errorBody = readStreamSafely(connection.errorStream)
                val summary = formatRequestSummary(endpointUrl, config.modelName, messages, system, statusCode, errorBody)
                val errorMessage = "$summary\nDetail: ${formatHttpError(statusCode, errorBody, endpointUrl)}"
                emit(LlmEvent.Error(errorMessage))
                return@flow
            }

            val parser = OpenAiSseParser()
            val inputStream = connection.inputStream
            inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (currentCoroutineContext().isActive) {
                    val line = reader.readLine() ?: break
                    val events = parser.parseLine(line)
                    for (event in events) {
                        emit(event)
                        if (event is LlmEvent.Done || event is LlmEvent.Error) {
                            return@flow
                        }
                    }
                }
            }

            if (!parser.isDone) {
                for (event in parser.finish()) {
                    emit(event)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val summary = formatRequestSummary(endpointUrl, config.modelName, messages, system, null, e.message ?: e.javaClass.simpleName)
            emit(LlmEvent.Error("$summary\nException: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(): String {
        val sb = StringBuilder()
        var errorMsg: String? = null
        stream(listOf(ChatMessage.user("Say OK"))).collect { event ->
            when (event) {
                is LlmEvent.Delta -> sb.append(event.text)
                is LlmEvent.Error -> errorMsg = event.message
                is LlmEvent.Done -> {}
                is LlmEvent.Reasoning -> {}
                is LlmEvent.ToolCall -> {}
                else -> {}
            }
        }
        return if (errorMsg != null) {
            errorMsg!!
        } else {
            val response = sb.toString().trim()
            if (response.isNotBlank()) response else "Connected successfully"
        }
    }

    private fun buildRequestPayload(
        config: ProviderConfig,
        messages: List<ChatMessage>,
        system: String?,
        tools: List<ToolDef>?,
    ): JSONObject {
        val root = JSONObject()
        root.put("model", config.modelName)
        root.put("stream", true)
        root.put("temperature", config.temperature.toDouble())

        if (!tools.isNullOrEmpty()) {
            val toolsArray = JSONArray()
            for (tool in tools) {
                val toolObj = JSONObject()
                toolObj.put("type", "function")
                val funcObj = JSONObject()
                funcObj.put("name", tool.name)
                funcObj.put("description", tool.description)
                funcObj.put("parameters", tool.parametersJsonSchema)
                toolObj.put("function", funcObj)
                toolsArray.put(toolObj)
            }
            root.put("tools", toolsArray)
            root.put("tool_choice", "auto")
        }

        val messagesArray = JSONArray()
        if (!system.isNullOrBlank()) {
            val systemMsg = JSONObject()
            systemMsg.put("role", ChatMessage.ROLE_SYSTEM)
            systemMsg.put("content", system)
            messagesArray.put(systemMsg)
        }

        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            if (msg.role == ChatMessage.ROLE_TOOL) {
                msgObj.put("content", msg.content)
                if (!msg.tool_call_id.isNullOrBlank()) {
                    msgObj.put("tool_call_id", msg.tool_call_id)
                }
            } else if (!msg.tool_calls.isNullOrEmpty()) {
                if (msg.content.isNotBlank()) {
                    msgObj.put("content", msg.content)
                } else {
                    msgObj.put("content", JSONObject.NULL)
                }
                val tcArray = JSONArray()
                for (tc in msg.tool_calls) {
                    val tcObj = JSONObject()
                    tcObj.put("id", tc.id)
                    tcObj.put("type", tc.type)
                    val funcObj = JSONObject()
                    funcObj.put("name", tc.function.name)
                    funcObj.put("arguments", tc.function.arguments)
                    tcObj.put("function", funcObj)
                    tcArray.put(tcObj)
                }
                msgObj.put("tool_calls", tcArray)
            } else {
                msgObj.put("content", msg.content)
            }
            messagesArray.put(msgObj)
        }

        root.put("messages", messagesArray)
        return root
    }

    private fun readStreamSafely(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 60_000
        const val READ_TIMEOUT_MS = 60_000

        fun formatRequestSummary(
            url: String,
            model: String,
            messages: List<ChatMessage>,
            system: String?,
            statusCode: Int?,
            rawResponse: String
        ): String {
            val roles = messages.joinToString(", ") { it.role }
            val systemCount = system?.length ?: 0
            val statusStr = statusCode?.toString() ?: "N/A"
            val responseSnippet = rawResponse.take(200)
            return "Request Summary:\n" +
                   "URL: $url\n" +
                   "Model: $model\n" +
                   "Roles: [$roles]\n" +
                   "System Prompt Chars: $systemCount\n" +
                   "HTTP Status: $statusStr\n" +
                   "Response Snippet: $responseSnippet"
        }

        fun normalizeChatCompletionsUrl(rawBaseUrl: String): String {
            val trimmed = rawBaseUrl.trim().removeSuffix("/")
            if (trimmed.isBlank()) return ""
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                else -> "$trimmed/chat/completions"
            }
        }

        fun formatHttpError(statusCode: Int, responseBody: String, url: String): String {
            val detail = extractErrorMessage(responseBody)
            return when (statusCode) {
                401 -> {
                    val baseMsg = "Authentication failed (HTTP 401) for $url: Invalid API key or unauthorized request."
                    if (detail.isNotBlank()) "$baseMsg $detail" else baseMsg
                }
                404 -> {
                    val baseMsg = "Resource not found (HTTP 404) for $url: The requested model or endpoint was not found."
                    if (detail.isNotBlank()) "$baseMsg $detail" else baseMsg
                }
                429 -> {
                    val baseMsg = "Rate limit exceeded (HTTP 429) for $url: Quota exhausted or too many requests."
                    if (detail.isNotBlank()) "$baseMsg $detail" else baseMsg
                }
                in 500..599 -> {
                    val baseMsg = "Provider server error (HTTP $statusCode) for $url: The AI server encountered an error."
                    if (detail.isNotBlank()) "$baseMsg $detail" else baseMsg
                }
                else -> {
                    val baseMsg = "HTTP request to $url failed with status $statusCode."
                    if (detail.isNotBlank()) "$baseMsg $detail" else baseMsg
                }
            }
        }

        private fun extractErrorMessage(body: String): String {
            if (body.isBlank()) return ""
            return try {
                val json = JSONObject(body)
                val errorObj = json.optJSONObject("error")
                val message = errorObj?.optString("message")
                    ?: json.optString("error", "")
                    ?: json.optString("detail", "")
                    ?: json.optString("message", "")
                message.trim()
            } catch (_: Exception) {
                body.trim().take(200)
            }
        }
    }
}

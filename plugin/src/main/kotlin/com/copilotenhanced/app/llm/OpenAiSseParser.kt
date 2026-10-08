package com.copilotenhanced.app.llm

import org.json.JSONObject

/**
 * Parses Server-Sent Events (SSE) from OpenAI-compatible `/chat/completions` endpoints.
 */
class OpenAiSseParser {

    var isDone: Boolean = false
        private set

    var finishReason: String? = null
        private set

    private class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder()
    )

    private val toolCallMap = mutableMapOf<Int, ToolCallAccumulator>()

    /**
     * Parses a single SSE stream line and returns any decoded [LlmEvent]s.
     */
    fun parseLine(line: String): List<LlmEvent> {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) {
            return emptyList()
        }

        if (trimmed.startsWith("data:")) {
            val payload = trimmed.substring(5).trim()
            return parseData(payload)
        }

        return emptyList()
    }

    /**
     * Parses the payload portion of an SSE `data:` line.
     */
    fun parseData(payload: String): List<LlmEvent> {
        if (payload == "[DONE]") {
            isDone = true
            return finish()
        }

        if (payload.isBlank()) {
            return emptyList()
        }

        return try {
            val json = JSONObject(payload)

            // Check for top-level error object
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                val message = errorObj?.optString("message")
                    ?: json.optString("error", "Unknown provider error")
                return listOf(LlmEvent.Error(message))
            }

            val choices = json.optJSONArray("choices") ?: return emptyList()
            if (choices.length() == 0) return emptyList()

            val firstChoice = choices.optJSONObject(0) ?: return emptyList()
            if (firstChoice.has("finish_reason") && !firstChoice.isNull("finish_reason")) {
                finishReason = firstChoice.optString("finish_reason", null)
            }

            val delta = firstChoice.optJSONObject("delta") ?: return emptyList()

            val content = when {
                delta.has("content") && !delta.isNull("content") -> delta.getString("content")
                delta.has("text") && !delta.isNull("text") -> delta.getString("text")
                else -> null
            }

            val reasoning = when {
                delta.has("reasoning_content") && !delta.isNull("reasoning_content") -> delta.getString("reasoning_content")
                delta.has("reasoning") && !delta.isNull("reasoning") -> delta.getString("reasoning")
                else -> null
            }

            val toolCalls = delta.optJSONArray("tool_calls")
            if (toolCalls != null) {
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.optJSONObject(i) ?: continue
                    val index = tc.optInt("index", 0)
                    val id = tc.optString("id", "")
                    val func = tc.optJSONObject("function")

                    val acc = toolCallMap.getOrPut(index) { ToolCallAccumulator() }
                    if (id.isNotBlank()) {
                        acc.id = id
                    }
                    if (func != null) {
                        val name = func.optString("name", "")
                        if (name.isNotBlank()) {
                            acc.name = name
                        }
                        val argsFrag = func.optString("arguments", "")
                        if (argsFrag.isNotEmpty()) {
                            acc.argumentsBuilder.append(argsFrag)
                        }
                    }
                }
            }

            val events = mutableListOf<LlmEvent>()
            if (!content.isNullOrEmpty()) {
                events.add(LlmEvent.Delta(content))
            }
            if (!reasoning.isNullOrEmpty()) {
                events.add(LlmEvent.Reasoning(reasoning))
            } else if (toolCalls != null && toolCalls.length() > 0 && events.isEmpty()) {
                events.add(LlmEvent.Reasoning("Executing tool calls..."))
            }
            events
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun finish(): List<LlmEvent> {
        val events = mutableListOf<LlmEvent>()
        if (toolCallMap.isNotEmpty()) {
            for ((_, acc) in toolCallMap.toSortedMap()) {
                val toolId = acc.id.ifBlank { "call_${System.currentTimeMillis()}" }
                events.add(LlmEvent.ToolCall(toolId, acc.name, acc.argumentsBuilder.toString()))
            }
            toolCallMap.clear()
        }
        if (!isDone) {
            isDone = true
            events.add(LlmEvent.Done)
        } else {
            events.add(LlmEvent.Done)
        }
        return events
    }
}

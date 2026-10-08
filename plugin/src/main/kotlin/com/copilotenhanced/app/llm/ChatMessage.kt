package com.copilotenhanced.app.llm

import kotlinx.serialization.Serializable

@Serializable
data class ToolCallDto(
    val id: String,
    val type: String = "function",
    val function: FunctionCallDto,
)

@Serializable
data class FunctionCallDto(
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val hidden: Boolean = false,
    val tool_calls: List<ToolCallDto>? = null,
    val tool_call_id: String? = null,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"

        fun system(content: String): ChatMessage = ChatMessage(ROLE_SYSTEM, content.take(100_000))
        fun user(content: String, hidden: Boolean = false): ChatMessage = ChatMessage(ROLE_USER, content.take(100_000), hidden)
        fun assistant(content: String): ChatMessage = ChatMessage(ROLE_ASSISTANT, content.take(100_000))
        fun assistantToolCalls(toolCalls: List<ToolCallDto>): ChatMessage = ChatMessage(ROLE_ASSISTANT, "", false, tool_calls = toolCalls)
        fun toolResult(toolCallId: String, content: String): ChatMessage = ChatMessage(ROLE_TOOL, content.take(100_000), false, tool_call_id = toolCallId)
    }
}

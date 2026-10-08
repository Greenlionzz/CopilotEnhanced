package com.copilotenhanced.app.llm

sealed interface LlmEvent {
    data class Delta(val text: String) : LlmEvent
    data class Reasoning(val text: String) : LlmEvent
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : LlmEvent
    data class Done(val finishReason: String? = null) : LlmEvent {
        companion object : LlmEvent {
            operator fun invoke(finishReason: String? = null): Done = Done(finishReason)
        }
    }
    data class Error(val message: String) : LlmEvent
}

package com.copilotenhanced.app.llm

import kotlinx.coroutines.flow.Flow

interface LlmClient {
    fun stream(messages: List<ChatMessage>, system: String? = null, tools: List<ToolDef>? = null): Flow<LlmEvent>
}

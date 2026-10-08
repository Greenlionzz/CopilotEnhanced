package com.copilotenhanced.app.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmClient(
    var eventsToEmit: List<LlmEvent>? = null,
    var responseDeltas: List<String> = listOf("Hello", " from", " FakeLlmClient!"),
    var shouldError: Boolean = false,
    var errorMessage: String = "Fake LLM error",
) : LlmClient {

    data class RecordedRequest(
        val messages: List<ChatMessage>,
        val system: String?,
        val tools: List<ToolDef>?,
    )

    private val _recordedRequests = mutableListOf<RecordedRequest>()
    val recordedRequests: List<RecordedRequest> get() = _recordedRequests

    override fun stream(messages: List<ChatMessage>, system: String?, tools: List<ToolDef>?): Flow<LlmEvent> = flow {
        _recordedRequests.add(RecordedRequest(messages, system, tools))

        if (eventsToEmit != null) {
            for (event in eventsToEmit!!) {
                emit(event)
            }
            return@flow
        }

        if (shouldError) {
            emit(LlmEvent.Error(errorMessage))
            return@flow
        }

        for (delta in responseDeltas) {
            emit(LlmEvent.Delta(delta))
        }
        emit(LlmEvent.Done())
    }
}

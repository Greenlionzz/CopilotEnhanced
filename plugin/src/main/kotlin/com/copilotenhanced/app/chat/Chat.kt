package com.copilotenhanced.app.chat

import com.copilotenhanced.app.llm.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: String,
    val title: String,
    val mode: ChatMode = ChatMode.PLAN,
    val messages: List<ChatMessage> = emptyList(),
    val plan: String? = null,
    val planVersion: Int = 0,
    val memory: String = "",
    val modeState: ModeState = ModeState.IDLE,
    val filePermissionGranted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

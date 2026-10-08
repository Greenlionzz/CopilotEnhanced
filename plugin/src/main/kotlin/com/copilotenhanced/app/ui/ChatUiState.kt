package com.copilotenhanced.app.ui

import com.copilotenhanced.app.chat.Chat
import com.copilotenhanced.app.chat.ChatMode
import com.copilotenhanced.app.chat.ModeState
import com.copilotenhanced.app.llm.ChatMessage

data class ChatUiState(
    val currentChat: Chat? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val progressMessage: String? = null,
    val error: String? = null,
    val historyList: List<Chat> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val currentMode: ChatMode = ChatMode.PLAN,
    val chatTitle: String = "New Chat",
    val modeState: ModeState = ModeState.IDLE,
    val showFilePermissionCard: Boolean = false,
    val isPaused: Boolean = false,
    val pauseMessage: String? = null,
    val buildRootPath: String? = null,
    val buildEntriesCount: Int = 0,
    val buildProbeResult: String = "ok",
)
